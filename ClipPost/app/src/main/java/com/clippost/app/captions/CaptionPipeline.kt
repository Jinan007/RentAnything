package com.clippost.app.captions

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/** Result of captioning: the new video file, or null with a note if there was nothing to caption. */
data class CaptionResult(val file: File?, val note: String?)

/** Runs the three steps in order: get the speech model, find the words, draw the captions. */
object CaptionPipeline {

    /**
     * @param output where the captioned video should end up
     * @param onStage called with a short description and a percentage (0-100) for each stage
     */
    suspend fun run(
        context: Context,
        source: Uri,
        output: File,
        onStage: suspend (String, Int) -> Unit
    ): CaptionResult {
        // 1. The speech model (downloaded once, about 40 MB).
        if (!VoskModel.isReady(context)) onStage(STAGE_MODEL, 0)
        val modelDir = VoskModel.ensure(context) { onStage(STAGE_MODEL, it) }

        // 2. Speech to text, with the time of every word.
        onStage(STAGE_LISTEN, 0)
        val words = Transcriber.transcribe(context, source, modelDir) { onStage(STAGE_LISTEN, it) }
        if (words.isEmpty()) {
            return CaptionResult(null, "No speech was found, so the video was uploaded without captions.")
        }

        // 3. Draw the captions onto a copy of the video.
        onStage(STAGE_BURN, 0)
        val lines = CaptionBuilder.group(words)
        val partial = File(output.path + ".part")
        try {
            CaptionBurner.burn(context, source, lines, partial) { onStage(STAGE_BURN, it) }
            output.delete()
            if (!partial.renameTo(output)) throw IOException("Could not save the captioned video.")
        } finally {
            partial.delete() // does nothing after a successful rename
        }
        return CaptionResult(output, null)
    }

    const val STAGE_MODEL = "Downloading speech model (once)"
    const val STAGE_LISTEN = "Listening for speech"
    const val STAGE_BURN = "Adding captions"
}
