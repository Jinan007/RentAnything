package com.clippost.app.captions

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/** Writes a copy of the video with the captions drawn onto it (Media3 Transformer, hardware accelerated). */
@OptIn(UnstableApi::class)
object CaptionBurner {

    /** Caption text height as a share of the video's shorter side. Raise it for bigger captions. */
    private const val TEXT_SIZE_RATIO = 0.055f

    suspend fun burn(
        context: Context,
        input: Uri,
        lines: List<CaptionLine>,
        output: File,
        onProgress: suspend (Int) -> Unit
    ) {
        val (width, height) = videoSize(context, input)
        val textPx = (min(width, height) * TEXT_SIZE_RATIO).toInt().coerceAtLeast(24)

        val overlay = CaptionOverlay(lines, textPx)
        val videoEffects = listOf<Effect>(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay)))
        val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        output.parentFile?.mkdirs()
        output.delete()

        // Transformer must be used on the main thread.
        withContext(Dispatchers.Main) {
            val finished = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        finished.completeExceptionally(exportException)
                    }
                })
                .build()

            transformer.start(item, output.absolutePath)
            val holder = ProgressHolder()
            var lastPercent = -1
            try {
                while (!finished.isCompleted) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val percent = holder.progress.coerceIn(0, 100)
                        if (percent != lastPercent && percent % 5 == 0) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                    delay(500)
                }
                finished.await() // throws if the export failed
            } catch (e: CancellationException) {
                transformer.cancel()
                throw e
            }
        }
    }

    /** The picture size as people see it (a phone video is often stored sideways and then rotated). */
    private fun videoSize(context: Context, uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) h to w else w to h
        } finally {
            retriever.release()
        }
    }
}
