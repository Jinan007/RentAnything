package com.clippost.app.captions

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/** Result of editing: the new video file, or null with a note if there was nothing to change. */
data class CaptionResult(val file: File?, val note: String?)

/**
 * Prepares the video before upload. Two optional jobs, which can be combined:
 *  - captions: get the speech model, find the words, draw them on the video
 *  - vertical: rebuild the landscape clip as a 1080 x 1920 split-screen (face cam on top, gameplay below)
 */
object CaptionPipeline {

    /**
     * @param output where the edited video should end up
     * @param captions draw word-by-word captions
     * @param vertical if not null, build the vertical split-screen version
     * @param onStage called with a short description and a percentage (0-100) for each stage
     */
    suspend fun run(
        context: Context,
        source: Uri,
        output: File,
        captions: Boolean,
        vertical: VerticalSpec?,
        onStage: suspend (String, Int) -> Unit
    ): CaptionResult {
        var lines = emptyList<CaptionLine>()
        var note: String? = null

        if (captions) {
            // 1. The speech model (downloaded once, about 40 MB).
            if (!VoskModel.isReady(context)) onStage(STAGE_MODEL, 0)
            val modelDir = VoskModel.ensure(context) { onStage(STAGE_MODEL, it) }

            // 2. Speech to text, with the time of every word.
            onStage(STAGE_LISTEN, 0)
            val words = Transcriber.transcribe(context, source, modelDir) { onStage(STAGE_LISTEN, it) }
            if (words.isEmpty()) {
                note = "No speech was found, so the video has no captions."
            } else {
                lines = CaptionBuilder.group(words)
            }
        }

        // Nothing to draw and no vertical edit asked for: upload the original as it is.
        if (vertical == null && lines.isEmpty()) return CaptionResult(null, note)

        // 3. Draw onto a copy of the video.
        val partial = File(output.path + ".part")
        try {
            if (vertical != null) {
                onStage(STAGE_VERTICAL, 0)
                VerticalComposer.render(context, source, vertical, lines, partial) { onStage(STAGE_VERTICAL, it) }
            } else {
                onStage(STAGE_BURN, 0)
                CaptionBurner.burn(context, source, lines, partial) { onStage(STAGE_BURN, it) }
            }
            output.delete()
            if (!partial.renameTo(output)) throw IOException("Could not save the edited video.")
        } finally {
            partial.delete() // does nothing after a successful rename
        }
        return CaptionResult(output, note)
    }

    const val STAGE_MODEL = "Downloading speech model (once)"
    const val STAGE_LISTEN = "Listening for speech"
    const val STAGE_BURN = "Adding captions"
    const val STAGE_VERTICAL = "Building the vertical video"
}
