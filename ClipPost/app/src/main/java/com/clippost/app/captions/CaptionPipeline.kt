package com.clippost.app.captions

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/** Result of editing: the new video file, or null with a note if there was nothing to change. */
data class CaptionResult(val file: File?, val note: String?)

/**
 * The on-device steps behind captions, the vertical layout and the automatic text.
 *  - transcribe: speech to words (needs the speech model, downloaded once)
 *  - edit: draw captions and/or the vertical layout onto a copy of the video
 */
object CaptionPipeline {

    /** Downloads the speech model if needed, then returns every spoken word with its time. */
    suspend fun transcribe(
        context: Context,
        source: Uri,
        onStage: suspend (String, Int) -> Unit
    ): List<Word> {
        if (!VoskModel.isReady(context)) onStage(STAGE_MODEL, 0)
        val modelDir = VoskModel.ensure(context) { onStage(STAGE_MODEL, it) }
        onStage(STAGE_LISTEN, 0)
        return Transcriber.transcribe(context, source, modelDir) { onStage(STAGE_LISTEN, it) }
    }

    /**
     * @param output where the edited video should end up
     * @param words the spoken words from [transcribe] (may be empty)
     * @param captions draw word-by-word captions
     * @param vertical if not null, build the vertical split-screen version
     */
    suspend fun edit(
        context: Context,
        source: Uri,
        output: File,
        words: List<Word>,
        captions: Boolean,
        vertical: VerticalSpec?,
        onStage: suspend (String, Int) -> Unit
    ): CaptionResult {
        val lines = if (captions) CaptionBuilder.group(words) else emptyList()
        val note = if (captions && words.isEmpty()) "No speech was found, so the video has no captions." else null

        // Nothing to draw and no vertical edit asked for: upload the original as it is.
        if (vertical == null && lines.isEmpty()) return CaptionResult(null, note)

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
