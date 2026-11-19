package com.clippost.app.captions

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
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
        val (width, height) = VideoInfo.size(context, input)
        val textPx = (min(width, height) * TEXT_SIZE_RATIO).toInt().coerceAtLeast(24)

        val overlay = CaptionOverlay(lines, textPx)
        val videoEffects = listOf<Effect>(OverlayEffect(listOf<TextureOverlay>(overlay)))
        val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build()).build()
        Exporter.export(context, composition, output, onProgress)
    }
}
