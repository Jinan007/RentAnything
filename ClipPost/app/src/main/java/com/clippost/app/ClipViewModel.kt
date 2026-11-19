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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Holds the clips, the YouTube connection and the uploads. It survives screen rotation. */
class ClipViewModel(app: Application) : AndroidViewModel(app) {

    private val uploader = YouTubeUploader(app.contentResolver)

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
            authState = AuthState.Failed("Sign-in was cancelled.")
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
        _clips.removeAll { it.id == id }
    }

    // ---- Upload ----------------------------------------------------------------------------

    /** Uploads one clip. Progress and the result show up in clip.upload. */
    fun upload(id: String) {
        val clip = _clips.firstOrNull { it.id == id } ?: return
        if (clip.upload is UploadState.Uploading) return

        if (clip.title.isBlank()) {
            setUpload(id, UploadState.Failed("Please give the clip a title."))
            return
        }

        setUpload(id, UploadState.Uploading(0))
        viewModelScope.launch {
            try {
                // Ask Google for a fresh token right before uploading (tokens expire after about an hour).
                val token = when (val auth = YouTubeAuth.authorize(getApplication())) {
                    is AuthResult.Token -> auth.value
                    is AuthResult.NeedsConsent -> {
                        authState = AuthState.Disconnected
                        throw UploadException("YouTube access was removed. Tap Connect, then try again.")
                    }
                    is AuthResult.Failed -> throw UploadException(auth.message)
                }

                val videoId = uploader.upload(
                    uri = clip.uri,
                    accessToken = token,
                    title = clip.title.trim(),
                    description = buildDescription(clip),
                    privacy = clip.privacy
                ) { percent ->
                    // Called on a background thread; hop back to the main thread to update the UI state.
                    viewModelScope.launch { setProgress(id, percent) }
                }
                setUpload(id, UploadState.Done(videoId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpload(id, UploadState.Failed(e.message ?: "Upload failed."))
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

    private fun setProgress(id: String, percent: Int) {
        // Ignore late progress updates that arrive after the upload already finished or failed.
        if (_clips.firstOrNull { it.id == id }?.upload is UploadState.Uploading) {
            setUpload(id, UploadState.Uploading(percent))
        }
    }

    private fun setUpload(id: String, state: UploadState) = change(id) { it.copy(upload = state) }

    private fun change(id: String, transform: (Clip) -> Clip) {
        val index = _clips.indexOfFirst { it.id == id }
        if (index >= 0) _clips[index] = transform(_clips[index])
    }
}
