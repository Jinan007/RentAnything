package com.clippost.app.captions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * The big title box on the split line: bold black text on a white rounded box, like the example videos.
 * It is shown for the first few seconds only, then the word-by-word captions take over.
 */
@OptIn(UnstableApi::class)
class TitleOverlay(
    text: String,
    private val centerY: Float,
    private val showForMs: Long,
    maxWidthPx: Int = 940,
    textSizePx: Float = 62f,
    /** false: black text on a white box. true: white text on a black box (for white backgrounds). */
    private val inverted: Boolean = false
) : BitmapOverlay() {

    private val box: Bitmap = draw(text, maxWidthPx, textSizePx)

    // Shown after the title time is over: a tiny fully transparent picture.
    private val nothing: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private val placement: OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, centerY)
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        if (presentationTimeUs / 1000 < showForMs) box else nothing

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = placement

    private fun draw(text: String, maxWidthPx: Int, textSizePx: Float): Bitmap {
        val padding = 28
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (inverted) Color.WHITE else Color.BLACK
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textWidth = maxWidthPx - 2 * padding
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        // Make the box only as wide as the longest line, so a short title gets a small box.
        var widest = 0f
        for (i in 0 until layout.lineCount) widest = maxOf(widest, layout.getLineWidth(i))
        val boxWidth = widest.toInt() + 2 * padding
        val boxHeight = layout.height + 2 * padding

        val bitmap = Bitmap.createBitmap(boxWidth, boxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (inverted) Color.BLACK else Color.WHITE }
        canvas.drawRoundRect(RectF(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat()), 34f, 34f, background)

        // The layout is textWidth wide with its lines centred in it. The box is only as wide as the
        // longest line, so shift the layout left until that longest line sits exactly inside the padding.
        canvas.save()
        canvas.translate(padding - (textWidth - widest) / 2f, padding.toFloat())
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}
