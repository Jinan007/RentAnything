package com.clippost.app.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clippost.app.AuthResult
import com.clippost.app.Privacy
import com.clippost.app.UploadException
import com.clippost.app.YouTubeAuth
import com.clippost.app.YouTubeCommenter
import com.clippost.app.YouTubeUploader
import com.clippost.app.captions.CaptionPipeline
import com.clippost.app.captions.CaptionResult
import com.clippost.app.captions.FacecamRegion
import com.clippost.app.captions.Metadata
import com.clippost.app.captions.TemplateWriter
import com.clippost.app.captions.VerticalSpec
import com.clippost.app.captions.Word
import com.clippost.app.data.AppDatabase
import com.clippost.app.data.ClipStatus
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

// Keys used to pass data into the worker and out of it.
const val KEY_CLIP_ID = "clipId"
const val KEY_URI = "uri"
const val KEY_TITLE = "title"
const val KEY_DESCRIPTION = "description"   // the user's own caption (#Shorts is added by the worker)
const val KEY_ADD_SHORTS = "addShorts"
const val KEY_PRIVACY = "privacy"
const val KEY_PUBLISH_AT = "publishAt"      // epoch milliseconds, or -1 for "publish now"
const val KEY_CAPTIONS = "captions"         // true = draw auto captions
const val KEY_VERTICAL = "vertical"         // true = rebuild as a vertical split-screen
const val KEY_FACECAM = "facecam"           // FacecamRegion name
const val KEY_TOP_TEXT = "topText"          // text box on the split line
const val KEY_AUTO_META = "autoMeta"        // true = write title/caption/tags/comment automatically
const val KEY_AUTO_TITLE = "autoTitle"      // true = the title was never typed, so the generated one is used
const val KEY_FILE_NAME = "fileName"        // the clip's file name, used by the automatic text
const val KEY_PERCENT = "percent"           // progress, shown while running
const val KEY_STAGE = "stage"               // while editing: what the worker is doing
const val KEY_VIDEO_ID = "videoId"          // result, on success
const val KEY_WARNING = "warning"           // result, on success: something to tell the user (optional)
const val KEY_ERROR = "error"               // reason, on failure
const val TAG_UPLOAD = "clippost_upload"

private const val MAX_ATTEMPTS = 4

/**
 * Prepares and uploads one clip in the background. Optional steps: speech, automatic text, editing.
 * WorkManager runs this even if the user leaves the app, retries when the network drops, and shows
 * progress in a notification. It also saves its progress and result in the database.
 */
class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val title: String get() = inputData.getString(KEY_TITLE).orEmpty()
    private val clipId: String? get() = inputData.getString(KEY_CLIP_ID)
    private val dao by lazy { AppDatabase.get(applicationContext).clipDao() }

    /** Where the edited copy lives. A retry finds it here and skips the slow editing step. */
    private val captionedFile: File
        get() = File(applicationContext.cacheDir, "captioned/${clipId ?: id}.mp4")

    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.uploadingInfo(applicationContext, id, title, 0)

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI)
            ?: return fail("The video file is missing. Add the clip again.")
        val source = Uri.parse(uriString)
        val privacy = runCatching { Privacy.valueOf(inputData.getString(KEY_PRIVACY).orEmpty()) }
            .getOrDefault(Privacy.PRIVATE)
        val publishAt = inputData.getLong(KEY_PUBLISH_AT, -1L).takeIf { it > 0 }
        val wantCaptions = inputData.getBoolean(KEY_CAPTIONS, false)
        val wantAuto = inputData.getBoolean(KEY_AUTO_META, false)
        val autoTitle = inputData.getBoolean(KEY_AUTO_TITLE, false)

        // Shows the notification and keeps the job alive. If Android refuses, we still try to upload.
        safeForeground(getForegroundInfo())
        record(ClipStatus.UPLOADING, 0)

        // ---- Step 1: listen to the speech (only if captions or automatic text need it) ----
        var words: List<Word> = emptyList()
        if (wantCaptions || wantAuto) {
            try {
                words = CaptionPipeline.transcribe(applicationContext, source) { stage, percent ->
                    report(stage, percent)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Includes native-library problems (Error), so a speech problem never crashes the app.
                Log.w("ClipPost", "Speech step failed", e)
                return fail(
                    "Listening to the video failed: ${e.message ?: e.javaClass.simpleName}. " +
                        "Turn off captions and automatic text to upload the original."
                )
            }
        }

        // ---- Step 2: write the text (automatic mode only; typed text always wins) ----
        val meta: Metadata? = if (wantAuto) {
            TemplateWriter.write(inputData.getString(KEY_FILE_NAME).orEmpty(), words)
        } else {
            null
        }
        val finalTitle = if (autoTitle && meta != null) meta.title else title
        val typedCaption = inputData.getString(KEY_DESCRIPTION).orEmpty()
        val caption = if (meta != null && typedCaption.isBlank()) meta.caption else typedCaption
        val description = withShorts(caption, inputData.getBoolean(KEY_ADD_SHORTS, false))

        // ---- Step 3: edit the video (captions and/or the vertical layout) ----
        val vertical = if (inputData.getBoolean(KEY_VERTICAL, false)) {
            VerticalSpec(
                region = runCatching { FacecamRegion.valueOf(inputData.getString(KEY_FACECAM).orEmpty()) }
                    .getOrDefault(FacecamRegion.TOP_RIGHT),
                topText = inputData.getString(KEY_TOP_TEXT).orEmpty().ifBlank { finalTitle }
            )
        } else {
            null
        }
        var videoUri = source
        var editNote: String? = null
        if (wantCaptions || vertical != null) {
            try {
                val made = editVideo(source, words, wantCaptions, vertical)
                if (made.file != null) videoUri = Uri.fromFile(made.file)
                editNote = made.note
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("ClipPost", "Editing failed", e)
                return fail(
                    "Editing the video failed: ${e.message ?: e.javaClass.simpleName}. " +
                        "Turn off captions and the vertical layout to upload the original instead."
                )
            }
            report(null, 0) // switch the card from "editing" to "uploading"
        }

        // ---- Step 4: a fresh access token (after the slow steps, so it can't have expired) ----
        val token = when (val auth = YouTubeAuth.authorize(applicationContext)) {
            is AuthResult.Token -> auth.value
            // A background job can't open Google's consent screen, so the user has to do it in the app.
            is AuthResult.NeedsConsent ->
                return fail("Google needs you to approve ClipPost again. Open ClipPost and tap Connect.")
            is AuthResult.Failed -> return retryOrFail(auth.message, retryable = true)
        }

        // ---- Step 5: upload, then the comment (a comment failure doesn't undo the upload) ----
        return try {
            val uploaded = YouTubeUploader(applicationContext.contentResolver).upload(
                uri = videoUri,
                accessToken = token,
                title = finalTitle,
                description = description,
                privacy = privacy,
                publishAtMillis = publishAt,
                tags = meta?.tags.orEmpty()
            ) { percent ->
                // Update the app and the notification every 5%, not on every chunk.
                if (percent % 5 == 0 || percent == 100) report(null, percent)
            }

            var commentNote: String? = null
            if (meta != null) {
                try {
                    YouTubeCommenter.post(token, uploaded.videoId, meta.comment)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    commentNote = "The video is up, but the comment wasn't posted: ${e.message}"
                }
            }

            val warning = listOfNotNull(editNote, uploaded.warning, commentNote)
                .joinToString(" ")
                .ifEmpty { null }
            record(
                ClipStatus.DONE, 100,
                videoId = uploaded.videoId,
                error = warning,          // for a finished upload this column holds the warning
                completedAt = System.currentTimeMillis()
            )
            Notifications.showDone(applicationContext, id, finalTitle, uploaded.videoId, warning)
            cleanUp()
            val output = if (warning != null) {
                workDataOf(KEY_VIDEO_ID to uploaded.videoId, KEY_WARNING to warning)
            } else {
                workDataOf(KEY_VIDEO_ID to uploaded.videoId)
            }
            Result.success(output)
        } catch (e: UploadException) {
            retryOrFail(e.message ?: "Upload failed.", e.retryable)
        } catch (e: IOException) {
            // No internet, timeout, dropped connection: try again later.
            retryOrFail("Connection problem. It will retry when the network is back.", retryable = true)
        }
    }

    /** Runs the edit, or reuses the file an earlier attempt already made. */
    private suspend fun editVideo(
        source: Uri,
        words: List<Word>,
        captions: Boolean,
        vertical: VerticalSpec?
    ): CaptionResult {
        val out = captionedFile
        if (out.exists() && out.length() > 0) return CaptionResult(out, null)
        return CaptionPipeline.edit(applicationContext, source, out, words, captions, vertical) { stage, percent ->
            report(stage, percent)
        }
    }

    /** Adds #Shorts to the end of a caption, unless it is already there or the switch is off. */
    private fun withShorts(text: String, addShorts: Boolean): String {
        val trimmed = text.trim()
        return when {
            !addShorts || trimmed.contains("#shorts", ignoreCase = true) -> trimmed
            trimmed.isEmpty() -> "#Shorts"
            else -> "$trimmed\n\n#Shorts"
        }
    }

    /**
     * Tells the app and the notification what is happening.
     * stage != null: editing (speech, captions, vertical). stage == null: uploading.
     */
    private suspend fun report(stage: String?, percent: Int) {
        if (stage != null) {
            setProgress(workDataOf(KEY_STAGE to stage, KEY_PERCENT to percent))
            record(ClipStatus.PROCESSING, percent, error = stage)
        } else {
            setProgress(workDataOf(KEY_PERCENT to percent))
            record(ClipStatus.UPLOADING, percent)
        }
        safeForeground(Notifications.uploadingInfo(applicationContext, id, title, percent, stage))
    }

    /** Like setForeground, but a refused foreground start doesn't fail the whole upload. */
    private suspend fun safeForeground(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ClipPost", "Could not start the foreground notification", e)
        }
    }

    /** Saves the clip's upload status in the database (no-op if the clip was removed). */
    private suspend fun record(
        status: String,
        percent: Int = 0,
        videoId: String? = null,
        error: String? = null,
        completedAt: Long? = null
    ) {
        val clip = clipId ?: return
        try {
            dao.updateStatus(clip, status, percent, videoId, error, completedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ClipPost", "Could not save the upload status", e)
        }
    }

    /** Deletes the temporary edited copy. Not done on a retry, which can reuse it. */
    private fun cleanUp() {
        runCatching { captionedFile.delete() }
    }

    private suspend fun retryOrFail(message: String, retryable: Boolean): Result =
        if (retryable && runAttemptCount < MAX_ATTEMPTS - 1) {
            record(ClipStatus.QUEUED, 0)
            Result.retry()
        } else {
            fail(message)
        }

    private suspend fun fail(message: String): Result {
        record(ClipStatus.FAILED, 0, error = message)
        Notifications.showFailed(applicationContext, id, title, message)
        cleanUp()
        return Result.failure(workDataOf(KEY_ERROR to message))
    }
}
