package com.clippost.app

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.clippost.app.work.KEY_DESCRIPTION
import com.clippost.app.work.KEY_ERROR
import com.clippost.app.work.KEY_PERCENT
import com.clippost.app.work.KEY_PRIVACY
import com.clippost.app.work.KEY_TITLE
import com.clippost.app.work.KEY_URI
import com.clippost.app.work.KEY_VIDEO_ID
import com.clippost.app.work.TAG_UPLOAD
import com.clippost.app.work.UploadWorker
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Holds the clips, the YouTube connection and the uploads. It survives screen rotation. */
class ClipViewModel(app: Application) : AndroidViewModel(app) {

    private val workManager = WorkManager.getInstance(app)

    /** clip id -> the WorkManager job uploading it (so we can watch and cancel it). */
    private val workIds = mutableMapOf<String, UUID>()

    private val _clips = mutableStateListOf<Clip>()
    val clips: List<Clip> get() = _clips

    // ---- YouTube connection ----------------------------------------------------------------

    var authState by mutableStateOf<AuthState>(AuthState.Disconnected)
        private set

    /** Set when Google needs the user to approve; the screen launches it and then calls consentRequestHandled(). */
    var consentRequest by mutableStateOf<PendingIntent?>(null)
        private set

    init {
        // If the user already approved earlier, this connects silently. Otherwise we stay "Not connected".
        viewModelScope.launch {
            if (YouTubeAuth.authorize(getApplication()) is AuthResult.Token) {
                authState = AuthState.Connected
            }
        }
    }

    fun connect() {
        authState = AuthState.Connecting
        viewModelScope.launch {
            when (val result = YouTubeAuth.authorize(getApplication())) {
                is AuthResult.Token -> authState = AuthState.Connected
                is AuthResult.NeedsConsent -> consentRequest = result.pendingIntent
                is AuthResult.Failed -> authState = AuthState.Failed(result.message)
            }
        }
    }

    fun consentRequestHandled() {
        consentRequest = null
    }

    fun onConsentResult(approved: Boolean, data: Intent?) {
        if (!approved) {
            authState = AuthState.Failed(YouTubeAuth.CANCELLED_MESSAGE)
            return
        }
        authState = when (val result = YouTubeAuth.fromConsentResult(getApplication(), data)) {
            is AuthResult.Token -> AuthState.Connected
            is AuthResult.Failed -> AuthState.Failed(result.message)
            is AuthResult.NeedsConsent -> AuthState.Failed("Google asked for approval again. Please try Connect once more.")
        }
    }

    // ---- Clips -----------------------------------------------------------------------------

    fun addClips(newClips: List<Clip>) {
        val known = _clips.map { it.uri }.toSet()
        _clips.addAll(newClips.filter { it.uri !in known })   // ignore clips already in the list
    }

    fun setTitle(id: String, title: String) = change(id) { it.copy(title = title.take(MAX_TITLE)) }

    fun setCaption(id: String, caption: String) = change(id) { it.copy(caption = caption.take(MAX_CAPTION)) }

    fun setPrivacy(id: String, privacy: Privacy) = change(id) { it.copy(privacy = privacy) }

    fun setAddShorts(id: String, addShorts: Boolean) = change(id) { it.copy(addShorts = addShorts) }

    fun remove(id: String) {
        cancel(id)
        _clips.removeAll { it.id == id }
    }

    // ---- Upload (runs in the background via WorkManager) -----------------------------------

    /** Queues one clip for upload. The job keeps running even if the app is closed. */
    fun upload(id: String) {
        val clip = _clips.firstOrNull { it.id == id } ?: return
        if (clip.upload is UploadState.Uploading || clip.upload is UploadState.Queued) return

        if (clip.title.isBlank()) {
            setUpload(id, UploadState.Failed("Please give the clip a title."))
            return
        }

        // Upload data goes in as plain text and a URI string; the worker reads it back.
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                workDataOf(
                    KEY_URI to clip.uri.toString(),
                    KEY_TITLE to clip.title.trim(),
                    KEY_DESCRIPTION to buildDescription(clip),
                    KEY_PRIVACY to clip.privacy.name
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_UPLOAD)
            .build()

        workIds[id] = request.id
        workManager.enqueue(request)
        setUpload(id, UploadState.Queued)
        watch(id, request.id)
    }

    /** Stops an upload that is queued or running. */
    fun cancel(id: String) {
        workIds[id]?.let { workManager.cancelWorkById(it) }
    }

    /** Copies WorkManager's status for one job into the clip, so the card always matches reality. */
    private fun watch(clipId: String, workId: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { info: WorkInfo? ->
                if (info == null) return@collect
                val state = when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UploadState.Queued
                    WorkInfo.State.RUNNING -> UploadState.Uploading(info.progress.getInt(KEY_PERCENT, 0))
                    WorkInfo.State.SUCCEEDED ->
                        UploadState.Done(info.outputData.getString(KEY_VIDEO_ID).orEmpty())
                    WorkInfo.State.FAILED ->
                        UploadState.Failed(info.outputData.getString(KEY_ERROR) ?: "Upload failed.")
                    WorkInfo.State.CANCELLED -> UploadState.Failed("Upload cancelled.")
                }
                setUpload(clipId, state)
            }
        }
    }

    /** The caption, plus #Shorts at the end if the switch is on. */
    private fun buildDescription(clip: Clip): String {
        val text = clip.caption.trim()
        return when {
            !clip.addShorts || text.contains("#shorts", ignoreCase = true) -> text
            text.isEmpty() -> "#Shorts"
            else -> "$text\n\n#Shorts"
        }
    }

    private fun setUpload(id: String, state: UploadState) = change(id) { it.copy(upload = state) }

    private fun change(id: String, transform: (Clip) -> Clip) {
        val index = _clips.indexOfFirst { it.id == id }
        if (index >= 0) _clips[index] = transform(_clips[index])
    }
}
