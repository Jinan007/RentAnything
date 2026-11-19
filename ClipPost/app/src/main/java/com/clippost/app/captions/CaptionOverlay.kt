package com.clippost.app.captions

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spannable
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay

/**
 * Draws the caption that belongs to each moment of the video.
 * One look for every video: bold white text on a dark box, the word being spoken in yellow,
 * placed near the bottom but above YouTube's own buttons.
 */
@OptIn(UnstableApi::class)
class CaptionOverlay(
    private val lines: List<CaptionLine>,
    private val textSizePx: Int
) : TextOverlay() {

    // x = 0 is the middle; y = -1 is the very bottom, so -0.55 sits about a quarter up from the bottom.
    private val placement = OverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, CAPTION_Y)
        .build()

    // A blank space (not an empty string) when nobody is speaking, so the overlay never has zero size.
    private val blank = SpannableString(" ")

    override fun getText(presentationTimeUs: Long): SpannableString {
        val ms = presentationTimeUs / 1000
        val line = lines.firstOrNull { ms >= it.startMs && ms < it.visibleUntilMs } ?: return blank

        val text = line.words.joinToString(" ") { it.text }
        val styled = SpannableString(text)
        val flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        styled.setSpan(AbsoluteSizeSpan(textSizePx), 0, text.length, flags)
        styled.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, flags)
        styled.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, flags)
        styled.setSpan(BackgroundColorSpan(BOX_COLOR), 0, text.length, flags)

        // Highlight the word that is being spoken right now (the last one that has already started).
        val active = line.words.lastOrNull { ms >= it.startMs } ?: line.words.first()
        var start = 0
        for (word in line.words) {
            val end = start + word.text.length
            if (word === active) styled.setSpan(ForegroundColorSpan(HIGHLIGHT_COLOR), start, end, flags)
            start = end + 1 // +1 for the space between words
        }
        return styled
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = placement

    private companion object {
        const val CAPTION_Y = -0.55f
        val BOX_COLOR = 0xB3000000.toInt()          // black, about 70% opaque
        val HIGHLIGHT_COLOR = 0xFFFFD60A.toInt()    // yellow
    }
}
