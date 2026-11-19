package com.clippost.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posts one comment on a video you own (YouTube commentThreads.insert).
 * Needs the youtube.force-ssl permission, which YouTubeAuth asks for.
 * YouTube's API can't pin a comment, so pin it yourself in YouTube Studio.
 */
object YouTubeCommenter {

    private const val THREADS_URL = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet"

    suspend fun post(accessToken: String, videoId: String, text: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "snippet", JSONObject()
                .put("videoId", videoId)
                .put(
                    "topLevelComment", JSONObject().put(
                        "snippet", JSONObject().put("textOriginal", text.take(10_000))
                    )
                )
        ).toString()

        val conn = URL(THREADS_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (_: IOException) {
                    ""
                }
                var reason = ""
                var detail = ""
                try {
                    val error = JSONObject(errorText).getJSONObject("error")
                    reason = error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
                    detail = error.optString("message")
                } catch (_: Exception) {
                    // Not JSON: the status code alone is shown.
                }
                throw IOException(YouTubeErrors.explain(code, reason, detail).message)
            }
        } finally {
            conn.disconnect()
        }
    }
}
