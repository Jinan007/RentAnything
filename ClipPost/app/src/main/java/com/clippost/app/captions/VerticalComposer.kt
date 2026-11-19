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
import androidx.media3.effect.Brightness
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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a landscape clip into a vertical 1080 x 1920 video, in one of three styles:
 *   WHITE / BLACK: the whole clip in the middle, a plain white or black background above and below,
 *                  the title on the top bar and the captions at the bottom of the picture.
 *   SPLIT:         face cam in the top part, gameplay in the bottom part, title box on the split line.
 * Sound comes from the original clip. The same file is read twice (the second read is the background
 * or the face cam).
 */
@OptIn(UnstableApi::class)
object VerticalComposer {

    /** In the split style the title box stays on screen this long. */
    private const val SPLIT_TITLE_MS = 4_000L

    /** Caption text height, as a share of the output width (1080). */
    private const val CAPTION_SIZE_RATIO = 0.075f

    /** Split style: captions sit a little below the split line so they never cover the title box. */
    private const val SPLIT_CAPTION_OFFSET_BELOW_SEAM = 0.25f

    /** Background styles: captions sit this far above the bottom edge of the picture (screen units). */
    private const val BARS_CAPTION_LIFT = 0.12f

    suspend fun render(
        context: Context,
        source: Uri,
        spec: VerticalSpec,
        captionLines: List<CaptionLine>,
        output: File,
        onProgress: suspend (Int) -> Unit
    ) {
        val composition = when (spec.style) {
            VerticalStyle.SPLIT -> splitComposition(source, spec, captionLines)
            VerticalStyle.WHITE, VerticalStyle.BLACK -> backgroundComposition(context, source, spec, captionLines)
        }
        Exporter.export(context, composition, output, onProgress)
    }

    // ---- WHITE / BLACK background -----------------------------------------------------------------

    private fun backgroundComposition(
        context: Context,
        source: Uri,
        spec: VerticalSpec,
        captionLines: List<CaptionLine>
    ): Composition {
        val canvasW = VerticalLayout.WIDTH
        val canvasH = VerticalLayout.HEIGHT
        val white = spec.style == VerticalStyle.WHITE

        // The clip, as large as fits on the canvas without cropping anything.
        val (srcW, srcH) = VideoInfo.size(context, source)
        val scale = min(canvasW.toFloat() / srcW, canvasH.toFloat() / srcH)
        val videoW = even((srcW * scale).roundToInt())
        val videoH = even((srcH * scale).roundToInt())
        // The picture covers the screen from -halfHeight to +halfHeight (-1 = bottom, +1 = top).
        val halfHeight = videoH.toFloat() / canvasH

        val video = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf<Effect>(
                        Presentation.createForWidthAndHeight(videoW, videoH, Presentation.LAYOUT_SCALE_TO_FIT)
                    )
                )
            )
            .build()

        // The background: the same video made completely white (or black) and stretched over the whole
        // canvas. It is drawn behind the real picture.
        val background = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf<Effect>(
                        Brightness(if (white) 1f else -1f),
                        Presentation.createForWidthAndHeight(canvasW, canvasH, Presentation.LAYOUT_STRETCH_TO_FIT)
                    )
                )
            )
            .build()

        val centred = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(0f, 0f).build()
        val compositor = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<Size>): Size = Size(canvasW, canvasH)

            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings = centred
        }

        val overlays = mutableListOf<TextureOverlay>()
        if (spec.topText.isNotBlank()) {
            // On the top bar, centred between the top of the picture and the top of the screen.
            val barCenterY = if (halfHeight < 0.92f) (1f + halfHeight) / 2f else 0.85f
            overlays += TitleOverlay(
                text = spec.topText.trim(),
                centerY = barCenterY,
                showForMs = Long.MAX_VALUE,           // stays for the whole video
                inverted = white                      // white text on black box, so it shows on white
            )
        }
        if (captionLines.isNotEmpty()) {
            overlays += CaptionOverlay(
                lines = captionLines,
                textSizePx = (canvasW * CAPTION_SIZE_RATIO).toInt(),
                centerY = -halfHeight + BARS_CAPTION_LIFT
            )
        }

        // First sequence = the main one (front, sets the timing, keeps the sound).
        return Composition.Builder(
            EditedMediaItemSequence.Builder(video).build(),
            EditedMediaItemSequence.Builder(background).build()
        )
            .setVideoCompositorSettings(compositor)
            .setEffects(Effects(emptyList(), finalEffects(overlays)))
            .build()
    }

    // ---- SPLIT screen -----------------------------------------------------------------------------

    private fun splitComposition(
        source: Uri,
        spec: VerticalSpec,
        captionLines: List<CaptionLine>
    ): Composition {
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
            overlays += TitleOverlay(spec.topText.trim(), VerticalLayout.seamY, SPLIT_TITLE_MS)
        }
        if (captionLines.isNotEmpty()) {
            overlays += CaptionOverlay(
                lines = captionLines,
                textSizePx = (width * CAPTION_SIZE_RATIO).toInt(),
                centerY = VerticalLayout.seamY - SPLIT_CAPTION_OFFSET_BELOW_SEAM
            )
        }

        return Composition.Builder(
            EditedMediaItemSequence.Builder(gameplay).build(),
            EditedMediaItemSequence.Builder(facecam).build()
        )
            .setVideoCompositorSettings(compositor)
            .setEffects(Effects(emptyList(), finalEffects(overlays)))
            .build()
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    private fun finalEffects(overlays: List<TextureOverlay>): List<Effect> =
        if (overlays.isEmpty()) emptyList() else listOf(OverlayEffect(overlays))

    /** Video encoders need even sizes. */
    private fun even(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)
}
