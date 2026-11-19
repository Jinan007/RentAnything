package com.clippost.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * A YouTube error we can explain to the user.
 * retryable = true  -> trying again later may work (server errors, timeouts).
 * retryable = false -> the same request will fail again (bad token, quota used up, rejected content).
 */
class UploadException(message: String, val retryable: Boolean = false) : IOException(message)

/** The new video's id, and a warning if YouTube did not do what we asked (for example it forced Private). */
data class UploadResult(val videoId: String, val warning: String?)

/**
 * Uploads one video with the YouTube Data API v3 (videos.insert, resumable protocol):
 *   1. POST the metadata  -> YouTube answers with a one-time upload URL (Location header).
 *   2. PUT the file bytes to that URL -> YouTube answers with the new video's JSON (incl. its id).
 */
class YouTubeUploader(private val resolver: ContentResolver) {

    suspend fun upload(
        uri: Uri,
        accessToken: String,
        title: String,
        description: String,
        privacy: Privacy,
        publishAtMillis: Long?,
        tags: List<String> = emptyList(),
        onProgress: suspend (Int) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        val size = fileSize(uri)
        if (size <= 0) throw UploadException("Could not read the video file. Try adding the clip again.")
        // Gallery videos report their type; our own captioned copy (a plain file) is always MP4.
        val mime = resolver.getType(uri) ?: "video/mp4"

        val uploadUrl = startSession(accessToken, title, description, privacy, publishAtMillis, tags, size, mime)
        sendBytes(uploadUrl, uri, size, mime, privacy, publishAtMillis, onProgress)
    }

    // ---- Step 1: create the upload session -------------------------------------------------

    private fun startSession(
        accessToken: String,
        title: String,
        description: String,
        privacy: Privacy,
        publishAtMillis: Long?,
        tags: List<String>,
        size: Long,
        mime: String
    ): String {
        // A scheduled video must be uploaded as Private; YouTube switches it to public at publishAt.
        val status = JSONObject()
            .put("privacyStatus", if (publishAtMillis != null) Privacy.PRIVATE.apiValue else privacy.apiValue)
            .put("selfDeclaredMadeForKids", false)
        if (publishAtMillis != null) {
            // RFC 3339 in UTC, whole seconds, e.g. 2026-11-25T18:30:00Z
            status.put("publishAt", Instant.ofEpochSecond(publishAtMillis / 1000).toString())
        }

        val body = JSONObject()
            .put(
                "snippet", JSONObject()
                    .put("title", clean(title))
                    .put("description", clean(description))
                    .put("categoryId", "22") // "People & Blogs"
                    .put("tags", JSONArray(tags.map { clean(it) }.filter { it.isNotBlank() }.take(15)))
            )
            .put("status", status)
            .toString()

        val conn = URL(SESSION_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("X-Upload-Content-Length", size.toString())
            conn.setRequestProperty("X-Upload-Content-Type", mime)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != 200) throw errorFrom(conn)
            return conn.getHeaderField("Location")
                ?: throw UploadException("YouTube did not return an upload address.", retryable = true)
        } finally {
            conn.disconnect()
        }
    }

    // ---- Step 2: send the video bytes ------------------------------------------------------

    private suspend fun sendBytes(
        uploadUrl: String,
        uri: Uri,
        size: Long,
        mime: String,
        requested: Privacy,
        publishAtMillis: Long?,
        onProgress: suspend (Int) -> Unit
    ): UploadResult {
        val conn = URL(uploadUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("Content-Type", mime)
            conn.setFixedLengthStreamingMode(size) // streams from disk; never loads the whole video in memory

            val input = resolver.openInputStream(uri)
                ?: throw UploadException("Could not open the video file.")
            input.use { source ->
                conn.outputStream.use { sink ->
                    val buffer = ByteArray(256 * 1024)
                    var sent = 0L
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive() // lets the user cancel
                        val n = source.read(buffer)
                        if (n < 0) break
                        sink.write(buffer, 0, n)
                        sent += n
                        val percent = ((sent * 100) / size).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }

            val code = conn.responseCode
            if (code != 200 && code != 201) throw errorFrom(conn)

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val videoId = json.optString("id")
            if (videoId.isEmpty()) throw UploadException("YouTube accepted the upload but returned no video id.")
            return UploadResult(videoId, checkResult(json, requested, publishAtMillis))
        } finally {
            conn.disconnect()
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /**
     * Compares what we asked for with what YouTube says it did.
     * Projects that have not passed YouTube's API compliance audit get their videos locked to Private.
     */
    private fun checkResult(json: JSONObject, requested: Privacy, publishAtMillis: Long?): String? {
        val status = json.optJSONObject("status") ?: return null
        val actual = status.optString("privacyStatus")
        return when {
            publishAtMillis != null && status.optString("publishAt").isEmpty() ->
                "Uploaded, but YouTube did not set the publish time, so the video stays Private. " +
                    "Google Cloud projects that have not passed YouTube's API audit can't schedule. " +
                    "You can set the date in YouTube Studio, or apply for the audit."

            publishAtMillis == null && requested != Privacy.PRIVATE && actual == "private" ->
                "Uploaded, but YouTube made the video Private even though you chose ${requested.label}. " +
                    "This happens when your Google Cloud project has not passed YouTube's API audit. " +
                    "You can change the visibility in YouTube Studio, or apply for the audit."

            else -> null
        }
    }

    private fun fileSize(uri: Uri): Long {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (column >= 0 && !cursor.isNull(column)) return cursor.getLong(column)
            }
        }
        return resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }

    /** YouTube rejects "<" and ">" in titles and descriptions. */
    private fun clean(text: String) = text.replace("<", "").replace(">", "")

    /** Reads YouTube's error JSON and turns it into a message the user can act on. */
    private fun errorFrom(conn: HttpURLConnection): UploadException {
        val code = conn.responseCode
        val text = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } catch (_: IOException) {
            ""
        }
        var reason = ""
        var detail = ""
        try {
            val error = JSONObject(text).getJSONObject("error")
            reason = error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
            detail = error.optString("message")
        } catch (_: Exception) {
            // Not JSON: fall back to the status code alone.
        }
        val explained = YouTubeErrors.explain(code, reason, detail)
        return UploadException(explained.message, explained.retryable)
    }

    private companion object {
        const val SESSION_URL =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"
    }
}
