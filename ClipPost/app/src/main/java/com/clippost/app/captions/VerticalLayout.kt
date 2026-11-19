package com.clippost.app.captions

import kotlin.math.roundToInt

/**
 * Where the face (reaction) cam sits in your landscape recording.
 * The numbers are fractions of the picture: left edge, top edge, width and height (0 to 1).
 * Change them here if your face cam is bigger, smaller or somewhere else.
 */
enum class FacecamRegion(
    val label: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    TOP_LEFT("Top left", 0f, 0f, 0.30f, 0.40f),
    TOP_RIGHT("Top right", 0.70f, 0f, 0.30f, 0.40f),
    BOTTOM_LEFT("Bottom left", 0f, 0.60f, 0.30f, 0.40f),
    BOTTOM_RIGHT("Bottom right", 0.70f, 0.60f, 0.30f, 0.40f),
    ZOOM("Zoom to centre", 0.25f, 0.25f, 0.50f, 0.50f)   // no face cam: a close-up of the middle
}

/** The numbers behind the vertical split-screen picture (1080 x 1920, the Shorts size). */
object VerticalLayout {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    /** The top (face cam) part takes this share of the height; gameplay gets the rest. */
    const val TOP_FRACTION = 0.40f

    /** Height of the face cam part in pixels (kept even, which video encoders need). */
    val topHeight: Int = ((HEIGHT * TOP_FRACTION).roundToInt() / 2) * 2
    val bottomHeight: Int = HEIGHT - topHeight

    // Positions in the -1 (bottom) to +1 (top) screen coordinates Media3 uses.
    /** Middle of the face cam part. */
    val topCenterY: Float = 1f - TOP_FRACTION

    /** Middle of the gameplay part. */
    val bottomCenterY: Float = -TOP_FRACTION

    /** Where the two parts meet. The title box and the captions are placed around this line. */
    val seamY: Float = 1f - 2f * TOP_FRACTION
}

/** What the user chose for the vertical version of one clip. */
data class VerticalSpec(
    val region: FacecamRegion,
    /** The big text box on the split line. */
    val topText: String
)
