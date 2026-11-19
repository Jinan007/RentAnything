package com.clippost.app.captions

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** One spoken word and when it was said, in milliseconds from the start of the video. */
data class Word(val text: String, val startMs: Long, val endMs: Long)

/** Speech-to-text that runs entirely on the phone (Vosk). Nothing is sent to a server. */
object Transcriber {

    suspend fun transcribe(
        context: Context,
        uri: Uri,
        modelDir: File,
        onProgress: suspend (Int) -> Unit
    ): List<Word> = withContext(Dispatchers.Default) {
        val words = mutableListOf<Word>()
        Model(modelDir.absolutePath).use { model ->
            Recognizer(model, 16_000f).use { recognizer ->
                recognizer.setWords(true) // ask for a start and end time for every word
                AudioDecoder.decode(
                    context = context,
                    uri = uri,
                    onChunk = { samples, length ->
                        // true = the recognizer finished a sentence and has its words ready
                        if (recognizer.acceptWaveForm(samples, length)) addWords(recognizer.result, words)
                    },
                    onProgress = onProgress
                )
                addWords(recognizer.finalResult, words)
            }
        }
        words
    }

    /** Vosk answers with JSON like {"result":[{"word":"hello","start":0.8,"end":1.2}], "text":"hello"}. */
    private fun addWords(json: String, into: MutableList<Word>) {
        val array = JSONObject(json).optJSONArray("result") ?: return
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            into.add(
                Word(
                    text = item.getString("word"),
                    startMs = (item.getDouble("start") * 1000).toLong(),
                    endMs = (item.getDouble("end") * 1000).toLong()
                )
            )
        }
    }
}
