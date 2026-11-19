package com.clippost.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clippost.app.AuthResult
import com.clippost.app.Privacy
import com.clippost.app.UploadException
import com.clippost.app.YouTubeAuth
import com.clippost.app.YouTubeUploader
import java.io.IOException

// Keys used to pass data into the worker and out of it.
const val KEY_URI = "uri"
const val KEY_TITLE = "title"
const val KEY_DESCRIPTION = "description"
const val KEY_PRIVACY = "privacy"
const val KEY_PERCENT = "percent"        // progress, shown while running
const val KEY_VIDEO_ID = "videoId"       // result, on success
const val KEY_ERROR = "error"            // reason, on failure
const val TAG_UPLOAD = "clippost_upload"

private const val MAX_ATTEMPTS = 4

/**
 * Uploads one clip in the background. WorkManager runs this even if the user leaves the app,
 * retries when the network drops, and shows progress in a notification.
 */
class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val title: String get() = inputData.getString(KEY_TITLE).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.uploadingInfo(applicationContext, id, title, 0)

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI)
            ?: return fail("The video file is missing. Add the clip again.")
        val privacy = runCatching { Privacy.valueOf(inputData.getString(KEY_PRIVACY).orEmpty()) }
            .getOrDefault(Privacy.PRIVATE)

        // Required before any long work: this shows the notification and keeps the job alive.
        setForeground(getForegroundInfo())

        val token = when (val auth = YouTubeAuth.authorize(applicationContext)) {
            is AuthResult.Token -> auth.value
            // A background job can't open Google's consent screen, so the user has to do it in the app.
            is AuthResult.NeedsConsent ->
                return fail("Google needs you to approve ClipPost again. Open ClipPost and tap Connect.")
            is AuthResult.Failed -> return retryOrFail(auth.message, retryable = true)
        }

        return try {
            val videoId = YouTubeUploader(applicationContext.contentResolver).upload(
                uri = Uri.parse(uriString),
                accessToken = token,
                title = title,
                description = inputData.getString(KEY_DESCRIPTION).orEmpty(),
                privacy = privacy
            ) { percent ->
                // Update the app and the notification every 5%, not on every chunk.
                if (percent % 5 == 0 || percent == 100) {
                    setProgress(workDataOf(KEY_PERCENT to percent))
                    setForeground(Notifications.uploadingInfo(applicationContext, id, title, percent))
                }
            }
            Notifications.showDone(applicationContext, id, title, videoId)
            Result.success(workDataOf(KEY_VIDEO_ID to videoId))
        } catch (e: UploadException) {
            retryOrFail(e.message ?: "Upload failed.", e.retryable)
        } catch (e: IOException) {
            // No internet, timeout, dropped connection: try again later.
            retryOrFail("Connection problem. It will retry when the network is back.", retryable = true)
        }
    }

    private fun retryOrFail(message: String, retryable: Boolean): Result =
        if (retryable && runAttemptCount < MAX_ATTEMPTS - 1) {
            Result.retry()
        } else {
            fail(message)
        }

    private fun fail(message: String): Result {
        Notifications.showFailed(applicationContext, id, title, message)
        return Result.failure(workDataOf(KEY_ERROR to message))
    }
}
