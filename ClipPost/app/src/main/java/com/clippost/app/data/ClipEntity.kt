package com.clippost.app.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clippost.app.Clip
import com.clippost.app.Privacy
import com.clippost.app.UploadState

/** The upload state is stored as plain text so the database stays simple. */
object ClipStatus {
    const val IDLE = "IDLE"
    const val QUEUED = "QUEUED"
    const val UPLOADING = "UPLOADING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

/** One row of the "clips" table: the queue and the upload history live in the same table. */
@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val fileName: String,
    val title: String,
    val caption: String,
    val privacy: String,
    val addShorts: Boolean,
    val publishAtMillis: Long?,
    val status: String,
    val percent: Int,
    val videoId: String?,
    val error: String?,
    val workId: String?,
    val createdAt: Long,
    val completedAt: Long?
)

fun Clip.toEntity(workId: String? = null): ClipEntity = ClipEntity(
    id = id,
    uri = uri.toString(),
    fileName = fileName,
    title = title,
    caption = caption,
    privacy = privacy.name,
    addShorts = addShorts,
    publishAtMillis = publishAtMillis,
    status = when (upload) {
        UploadState.Idle -> ClipStatus.IDLE
        UploadState.Queued -> ClipStatus.QUEUED
        is UploadState.Uploading -> ClipStatus.UPLOADING
        is UploadState.Done -> ClipStatus.DONE
        is UploadState.Failed -> ClipStatus.FAILED
    },
    percent = (upload as? UploadState.Uploading)?.percent ?: 0,
    videoId = (upload as? UploadState.Done)?.videoId,
    // The "error" column holds the failure message, or for a finished upload an optional warning.
    error = when (upload) {
        is UploadState.Failed -> upload.message
        is UploadState.Done -> upload.note
        else -> null
    },
    workId = workId,
    createdAt = createdAt,
    completedAt = completedAt
)

fun ClipEntity.toClip(): Clip = Clip(
    id = id,
    uri = Uri.parse(uri),
    fileName = fileName,
    title = title,
    caption = caption,
    privacy = runCatching { Privacy.valueOf(privacy) }.getOrDefault(Privacy.PRIVATE),
    addShorts = addShorts,
    publishAtMillis = publishAtMillis,
    upload = when (status) {
        ClipStatus.QUEUED -> UploadState.Queued
        ClipStatus.UPLOADING -> UploadState.Uploading(percent)
        ClipStatus.DONE -> UploadState.Done(videoId.orEmpty(), note = error)
        ClipStatus.FAILED -> UploadState.Failed(error ?: "Upload failed.")
        else -> UploadState.Idle
    },
    createdAt = createdAt,
    completedAt = completedAt
)
