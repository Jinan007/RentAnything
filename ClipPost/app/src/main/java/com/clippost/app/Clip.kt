package com.clippost.app

import android.net.Uri
import java.util.UUID

const val MAX_TITLE = 100          // YouTube title limit
const val MAX_CAPTION = 5000       // YouTube description limit (approx.)

/** A scheduled publish time must be at least this far in the future. */
const val MIN_SCHEDULE_LEAD_MILLIS = 15 * 60 * 1000L

/** YouTube's privacyStatus values. */
enum class Privacy(val label: String, val apiValue: String) {
    PUBLIC("Public", "public"),
    UNLISTED("Unlisted", "unlisted"),
    PRIVATE("Private", "private")
}

/** Where a clip is in the upload process. */
sealed interface UploadState {
    data object Idle : UploadState
    data object Queued : UploadState                  // waiting for the network or a free worker
    data class Uploading(val percent: Int) : UploadState
    data class Done(val videoId: String) : UploadState
    data class Failed(val message: String) : UploadState
}

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val title: String,
    val caption: String = "",
    val privacy: Privacy = Privacy.PRIVATE,
    val addShorts: Boolean = false,
    /** If set, the video uploads as Private and YouTube makes it public at this time (epoch milliseconds). */
    val publishAtMillis: Long? = null,
    val upload: UploadState = UploadState.Idle,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
