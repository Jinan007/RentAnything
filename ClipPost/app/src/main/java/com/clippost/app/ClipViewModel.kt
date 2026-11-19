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
import com.clippost.app.data.AppDatabase
import com.clippost.app.data.ClipStatus
import com.clippost.app.data.toClip
import com.clippost.app.data.toEntity
import com.clippost.app.work.KEY_CLIP_ID
import com.clippost.app.work.KEY_DESCRIPTION
import com.clippost.app.work.KEY_ERROR
import com.clippost.app.work.KEY_PERCENT
import com.clippost.app.work.KEY_PRIVACY
import com.clippost.app.work.KEY_PUBLISH_AT
import com.clippost.app.work.KEY_TITLE
import com.clippost.app.work.KEY_URI
import com.clippost.app.work.KEY_VIDEO_ID
import com.clippost.app.work.KEY_WARNING
import com.clippost.app.work.TAG_UPLOAD
import com.clippost.app.work.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Holds the clips (queue + history), the YouTube connection and the uploads.
 * The screen reads the in-memory list; every change is also saved in the Room database.
 */
class ClipViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).clipDao()
    private val workManager = WorkManager.getInstance(app)

    /** clip id -> the WorkManager job uploading it (so we can watch and cancel it). */
    private val workIds = mutableMapOf<String, UUID>()

    /** Pending "save the edits" jobs, so typing doesn't write to the database on every keystroke. */
    private val editJobs = mutableMapOf<String, Job>()

    private val _clips = mutableStateListOf<Clip>()
    val clips: List<Clip> get() = _clips

    // ---- YouTube connection ----------------------------------------------------------------

    var authState by mutableStateOf<AuthState>(AuthState.Disconnected)
        private set

    /** Set when Google needs the user to approve; the screen launches it and then calls consentRequestHandled(). */
    var consentRequest by mutableStateOf<PendingIntent?>(null)
        private set

    init {
        viewModelScope.launch { restoreSavedClips() }

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

    // ---- Saved queue and history -----------------------------------------------------------

    /** Loads clips from the database and reconnects to uploads that were running when the app closed. */
    private suspend fun restoreSavedClips() {
        val saved = withContext(Dispatchers.IO) { dao.getAll() }
        for (entity in saved) {
            val clip = entity.toClip()
            _clips.add(clip)

            val workId = entity.workId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (workId != null) workIds[clip.id] = workId

            val active = clip.upload is UploadState.Queued || clip.upload is UploadState.Uploading
            if (active) {
                val known = workId != null && workManager.getWorkInfoByIdFlow(workId).first() != null
                if (known) {
                    watch(clip.id, workId!!)
                } else {
                    // The job is gone (for example the app data was cleared mid-upload).
                    setUpload(clip.id, UploadState.Failed("The upload was interrupted. Tap Retry."))
                    saveStatus(clip.id)
                }
            }
        }
    }

    // ---- Clips -----------------------------------------------------------------------------

    fun addClips(newClips: List<Clip>) {
        // A clip that is already in history can be picked again (to upload it a second time).
        val known = _clips.filter { it.upload !is UploadState.Done }.map { it.uri }.toSet()
        val fresh = newClips.filter { it.uri !in known }
        if (fresh.isEmpty()) return
        _clips.addAll(fresh)
        viewModelScope.launch(Dispatchers.IO) { fresh.forEach { dao.insert(it.toEntity()) } }
    }

    fun setTitle(id: String, title: String) = edit(id) { it.copy(title = title.take(MAX_TITLE)) }

    fun setCaption(id: String, caption: String) = edit(id) { it.copy(caption = caption.take(MAX_CAPTION)) }

    fun setPrivacy(id: String, privacy: Privacy) = edit(id) { it.copy(privacy = privacy) }

    fun setAddShorts(id: String, addShorts: Boolean) = edit(id) { it.copy(addShorts = addShorts) }

    /** Schedules publishing (or clears the schedule with null). Scheduled videos are always Private until then. */
    fun setPublishAt(id: String, millis: Long?) = edit(id) {
        it.copy(publishAtMillis = millis, privacy = if (millis != null) Privacy.PRIVATE else it.privacy)
    }

    /** Removes a clip from the queue or from the history. (A video already on YouTube stays there.) */
    fun remove(id: String) {
        cancel(id)
        editJobs.remove(id)?.cancel()
        _clips.removeAll { it.id == id }
        workIds.remove(id)
        viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }
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
        val publishAt = clip.publishAtMillis
        if (publishAt != null && publishAt < System.currentTimeMillis() + MIN_SCHEDULE_LEAD_MILLIS) {
            setUpload(id, UploadState.Failed("The scheduled time is too soon or has passed. Change the schedule time."))
            return
        }

        // Upload data goes in as plain text and numbers; the worker reads it back.
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                workDataOf(
                    KEY_CLIP_ID to clip.id,
                    KEY_URI to clip.uri.toString(),
                    KEY_TITLE to clip.title.trim(),
                    KEY_DESCRIPTION to buildDescription(clip),
                    KEY_PRIVACY to clip.privacy.name,
                    KEY_PUBLISH_AT to (publishAt ?: -1L)
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_UPLOAD)
            .build()

        workIds[id] = request.id
        setUpload(id, UploadState.Queued)
        editJobs.remove(id)?.cancel()

        viewModelScope.launch {
            // Save everything BEFORE the worker starts, so the worker's later updates always win.
            withContext(Dispatchers.IO) {
                dao.updateEdits(clip.id, clip.title, clip.caption, clip.privacy.name, clip.addShorts, clip.publishAtMillis)
                dao.setWorkId(clip.id, request.id.toString())
                dao.updateStatus(clip.id, ClipStatus.QUEUED, 0, null, null, null)
            }
            workManager.enqueue(request)
            watch(id, request.id)
        }
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
                        UploadState.Done(
                            info.outputData.getString(KEY_VIDEO_ID).orEmpty(),
                            note = info.outputData.getString(KEY_WARNING)
                        )
                    WorkInfo.State.FAILED ->
                        UploadState.Failed(info.outputData.getString(KEY_ERROR) ?: "Upload failed.")
                    WorkInfo.State.CANCELLED -> UploadState.Failed("Upload cancelled.")
                }
                setUpload(clipId, state)
                // The worker saves success and failure itself; a cancelled worker can't, so we do.
                if (info.state == WorkInfo.State.CANCELLED) saveStatus(clipId)
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

    // ---- Helpers ---------------------------------------------------------------------------

    private fun setUpload(id: String, state: UploadState) = change(id) {
        val finished = state is UploadState.Done && it.upload !is UploadState.Done
        it.copy(upload = state, completedAt = if (finished) System.currentTimeMillis() else it.completedAt)
    }

    private fun change(id: String, transform: (Clip) -> Clip) {
        val index = _clips.indexOfFirst { it.id == id }
        if (index >= 0) _clips[index] = transform(_clips[index])
    }

    /** Changes a clip and saves the edit shortly after the user stops typing. */
    private fun edit(id: String, transform: (Clip) -> Clip) {
        change(id, transform)
        editJobs[id]?.cancel()
        editJobs[id] = viewModelScope.launch {
            delay(400)
            val clip = _clips.firstOrNull { it.id == id } ?: return@launch
            withContext(Dispatchers.IO) {
                dao.updateEdits(clip.id, clip.title, clip.caption, clip.privacy.name, clip.addShorts, clip.publishAtMillis)
            }
        }
    }

    private fun saveStatus(id: String) {
        val entity = _clips.firstOrNull { it.id == id }?.toEntity() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateStatus(entity.id, entity.status, entity.percent, entity.videoId, entity.error, entity.completedAt)
        }
    }
}
