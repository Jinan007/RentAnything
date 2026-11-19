package com.clippost.app.captions

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * The speech model Vosk needs (small English model, about 40 MB).
 * It is too big to ship inside the app, so it is downloaded once and kept in the app's private storage.
 * To caption another language, change MODEL_NAME to a model from https://alphacephei.com/vosk/models
 */
object VoskModel {

    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    private const val READY_MARKER = ".ready"

    fun dir(context: Context): File = File(context.filesDir, "vosk/$MODEL_NAME")

    fun isReady(context: Context): Boolean = File(dir(context), READY_MARKER).exists()

    /** Returns the model folder, downloading and unpacking it first if needed. */
    suspend fun ensure(context: Context, onProgress: suspend (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = dir(context)
            if (isReady(context)) return@withContext target

            val root = File(context.filesDir, "vosk").apply { mkdirs() }
            target.deleteRecursively() // remove any half-finished earlier attempt

            val zip = File(context.cacheDir, "$MODEL_NAME.zip")
            try {
                download(zip, onProgress)
                unzip(zip, root)
            } finally {
                zip.delete()
            }

            if (!File(target, "am").exists()) {
                throw IOException("The speech model download looks incomplete. Please try again.")
            }
            File(target, READY_MARKER).writeText("ok")
            target
        }

    private suspend fun download(dest: File, onProgress: suspend (Int) -> Unit) {
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            if (conn.responseCode != 200) {
                throw IOException("Could not download the speech model (HTTP ${conn.responseCode}).")
            }
            val total = conn.contentLengthLong
            var done = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent && percent % 5 == 0) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun unzip(zip: File, targetRoot: File) {
        val rootPath = targetRoot.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(targetRoot, entry.name)
                // Safety: never write outside the model folder ("zip slip").
                if (!out.canonicalPath.startsWith(rootPath)) throw IOException("Unexpected path in the model file.")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
            }
        }
    }
}
