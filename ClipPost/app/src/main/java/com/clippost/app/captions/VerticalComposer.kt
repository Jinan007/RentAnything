package com.clippost.app.captions

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import java.io.File

/**
 * Turns a landscape clip into a vertical 1080 x 1920 split-screen video:
 *   top    = the face cam, cut out of the original clip
 *   bottom = the gameplay (the original clip, filled to the width)
 * with a title box on the split line and, optionally, word-by-word captions.
 * Sound comes from the original clip. The same file is read twice, once for each half.
 */
@OptIn(UnstableApi::class)
object VerticalComposer {

    /** The title box stays on screen this long. */
    private const val TITLE_SHOW_MS = 4_000L

    /** Caption text height, as a share of the output width (1080). */
    private const val CAPTION_SIZE_RATIO = 0.075f

    /** Captions sit a little below the split line so they never cover the title box. */
    private const val CAPTION_OFFSET_BELOW_SEAM = 0.25f

    suspend fun render(
        context: Context,
        source: Uri,
        spec: VerticalSpec,
        captionLines: List<CaptionLine>,
        output: File,
        onProgress: suspend (Int) -> Unit
    ) {
        val width = VerticalLayout.WIDTH
        val region = spec.region

        // Media3's Crop works in screen coordinates: -1 .. +1, with +1 at the top.
        val crop = Crop(
            /* left = */ -1f + 2f * region.left,
            /* right = */ -1f + 2f * (region.left + region.width),
            /* bottom = */ 1f - 2f * (region.top + region.height),
            /* top = */ 1f - 2f * region.top
        )

        // Input 0 (first sequence) is the "main" one: it keeps the sound and sets the timing.
        val gameplay = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf<Effect>(
                        Presentation.createForWidthAndHeight(
                            width, VerticalLayout.bottomHeight, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        )
                    )
                )
            )
            .build()

        val facecam = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setRemoveAudio(true) // one soundtrack is enough
            .setEffects(
                Effects(
                    emptyList(),
                    listOf<Effect>(
                        crop,
                        Presentation.createForWidthAndHeight(
                            width, VerticalLayout.topHeight, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        )
                    )
                )
            )
            .build()

        // Where each half goes on the 1080 x 1920 canvas.
        val compositor = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<Size>): Size =
                Size(VerticalLayout.WIDTH, VerticalLayout.HEIGHT)

            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
                val centerY = if (inputId == 0) VerticalLayout.bottomCenterY else VerticalLayout.topCenterY
                return StaticOverlaySettings.Builder().setBackgroundFrameAnchor(0f, centerY).build()
            }
        }

        // Text drawn on top of the finished picture: the title box, then the captions.
        val overlays = mutableListOf<TextureOverlay>()
        if (spec.topText.isNotBlank()) {
            overlays += TitleOverlay(spec.topText.trim(), VerticalLayout.seamY, TITLE_SHOW_MS)
        }
        if (captionLines.isNotEmpty()) {
            overlays += CaptionOverlay(
                lines = captionLines,
                textSizePx = (width * CAPTION_SIZE_RATIO).toInt(),
                centerY = VerticalLayout.seamY - CAPTION_OFFSET_BELOW_SEAM
            )
        }
        val finalEffects = if (overlays.isEmpty()) emptyList<Effect>() else listOf<Effect>(OverlayEffect(overlays))

        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(gameplay).build(),
            EditedMediaItemSequence.Builder(facecam).build()
        )
            .setVideoCompositorSettings(compositor)
            .setEffects(Effects(emptyList(), finalEffects))
            .build()

        Exporter.export(context, composition, output, onProgress)
    }
}
