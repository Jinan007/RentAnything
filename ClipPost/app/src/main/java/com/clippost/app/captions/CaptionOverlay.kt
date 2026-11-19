package com.clippost.app.captions

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay

/**
 * Draws the caption that belongs to each moment of the video.
 * One look for every video: bold white text on a dark box, the word being spoken in yellow,
 * placed near the bottom but above YouTube's own buttons.
 *
 * Media3 maps this text onto the video pixel for pixel, so textSizePx is the real text height in pixels.
 */
@OptIn(UnstableApi::class)
class CaptionOverlay(
    private val lines: List<CaptionLine>,
    private val textSizePx: Int
) : TextOverlay() {

    // x = 0 is the middle; y = -1 is the very bottom, so -0.55 sits about a quarter up from the bottom.
    private val placement: OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, CAPTION_Y)
        .build()

    // A blank space (not an empty string) when nobody is speaking, so the overlay never has zero size.
    private val blank = SpannableString(" ")

    // Media3 redraws its bitmap whenever it gets a different text object, so hand back the same
    // object while the caption and the highlighted word have not changed.
    private var cachedKey = -1
    private var cachedText: SpannableString = blank

    override fun getText(presentationTimeUs: Long): SpannableString {
        val ms = presentationTimeUs / 1000
        val lineIndex = lines.indexOfFirst { ms >= it.startMs && ms < it.visibleUntilMs }
        if (lineIndex < 0) return blank

        val line = lines[lineIndex]
        // The highlighted word is the last one that has already started.
        val activeIndex = line.words.indexOfLast { ms >= it.startMs }.coerceAtLeast(0)

        val key = lineIndex * 1000 + activeIndex
        if (key == cachedKey) return cachedText

        val text = line.words.joinToString(" ") { it.text }
        val styled = SpannableString(text)
        val flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        styled.setSpan(AbsoluteSizeSpan(textSizePx), 0, text.length, flags)
        styled.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, flags)
        styled.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, flags)
        styled.setSpan(BackgroundColorSpan(BOX_COLOR), 0, text.length, flags)

        var start = 0
        line.words.forEachIndexed { index, word ->
            val end = start + word.text.length
            if (index == activeIndex) styled.setSpan(ForegroundColorSpan(HIGHLIGHT_COLOR), start, end, flags)
            start = end + 1 // +1 for the space between words
        }

        cachedKey = key
        cachedText = styled
        return styled
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = placement

    private companion object {
        const val CAPTION_Y = -0.55f
        val BOX_COLOR = 0xB3000000.toInt()          // black, about 70% opaque
        val HIGHLIGHT_COLOR = 0xFFFFD60A.toInt()    // yellow
    }
}
