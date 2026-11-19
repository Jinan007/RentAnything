package com.clippost.app

import android.net.Uri
import java.util.UUID

const val MAX_TITLE = 100          // YouTube title limit
const val MAX_CAPTION = 5000       // YouTube description limit (approx.)

/** YouTube's privacyStatus values. We use them again in step 4. */
enum class Privacy(val label: String, val apiValue: String) {
    PUBLIC("Public", "public"),
    UNLISTED("Unlisted", "unlisted"),
    PRIVATE("Private", "private")
}

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val title: String,
    val caption: String = "",
    val privacy: Privacy = Privacy.PRIVATE
)
