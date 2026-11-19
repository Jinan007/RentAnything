package com.clippost.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** An upload problem with a message that is safe to show to the user. */
class UploadException(message: String) : IOException(message)

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
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val size = fileSize(uri)
        if (size <= 0) throw UploadException("Could not read the video file. Try adding the clip again.")
        val mime = resolver.getType(uri) ?: "video/*"

        val uploadUrl = startSession(accessToken, title, description, privacy, size, mime)
        sendBytes(uploadUrl, uri, size, mime, onProgress)
    }

    // ---- Step 1: create the upload session -------------------------------------------------

    private fun startSession(
        accessToken: String,
        title: String,
        description: String,
        privacy: Privacy,
        size: Long,
        mime: String
    ): String {
        val body = JSONObject()
            .put(
                "snippet", JSONObject()
                    .put("title", clean(title))
                    .put("description", clean(description))
                    .put("categoryId", "22") // "People & Blogs"
            )
            .put(
                "status", JSONObject()
                    .put("privacyStatus", privacy.apiValue)
                    .put("selfDeclaredMadeForKids", false)
            )
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

            if (conn.responseCode != 200) throw UploadException(describeError(conn))
            return conn.getHeaderField("Location")
                ?: throw UploadException("YouTube did not return an upload address.")
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
        onProgress: (Int) -> Unit
    ): String {
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
            if (code != 200 && code != 201) throw UploadException(describeError(conn))
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return json.optString("id").ifEmpty {
                throw UploadException("YouTube accepted the upload but returned no video id.")
            }
        } finally {
            conn.disconnect()
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

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

    /** Turns YouTube's error JSON into one readable line. Step 7 adds friendly help for common cases. */
    private fun describeError(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val text = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } catch (_: IOException) {
            ""
        }
        return try {
            val error = JSONObject(text).getJSONObject("error")
            val reason = error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
            val message = error.optString("message")
            "YouTube error $code" + (if (reason.isNotEmpty()) " ($reason)" else "") + ": $message"
        } catch (_: Exception) {
            "YouTube error $code"
        }
    }

    private companion object {
        const val SESSION_URL =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"
    }
}
