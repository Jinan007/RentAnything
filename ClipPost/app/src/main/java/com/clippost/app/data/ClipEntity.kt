package com.clippost.app.data

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clippost.app.Clip
import com.clippost.app.Privacy
import com.clippost.app.UploadState
import com.clippost.app.captions.FacecamRegion

/** The upload state is stored as plain text so the database stays simple. */
object ClipStatus {
    const val IDLE = "IDLE"
    const val QUEUED = "QUEUED"
    const val PROCESSING = "PROCESSING"
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
    @ColumnInfo(defaultValue = "0") val captions: Boolean,
    @ColumnInfo(defaultValue = "0") val vertical: Boolean,
    @ColumnInfo(defaultValue = "'TOP_RIGHT'") val facecam: String,
    @ColumnInfo(defaultValue = "''") val topText: String,
    @ColumnInfo(defaultValue = "0") val autoMeta: Boolean,
    val publishAtMillis: Long?,
    val status: String,
    val percent: Int,
    val videoId: String?,
    /** Failed: the error. Done: an optional warning. Processing: the stage text. */
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
    captions = captions,
    vertical = vertical,
    facecam = facecam.name,
    topText = topText,
    autoMeta = autoMeta,
    publishAtMillis = publishAtMillis,
    status = when (upload) {
        UploadState.Idle -> ClipStatus.IDLE
        UploadState.Queued -> ClipStatus.QUEUED
        is UploadState.Processing -> ClipStatus.PROCESSING
        is UploadState.Uploading -> ClipStatus.UPLOADING
        is UploadState.Done -> ClipStatus.DONE
        is UploadState.Failed -> ClipStatus.FAILED
    },
    percent = when (upload) {
        is UploadState.Processing -> upload.percent
        is UploadState.Uploading -> upload.percent
        else -> 0
    },
    videoId = (upload as? UploadState.Done)?.videoId,
    error = when (upload) {
        is UploadState.Failed -> upload.message
        is UploadState.Done -> upload.note
        is UploadState.Processing -> upload.stage
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
    captions = captions,
    vertical = vertical,
    facecam = runCatching { FacecamRegion.valueOf(facecam) }.getOrDefault(FacecamRegion.TOP_RIGHT),
    topText = topText,
    autoMeta = autoMeta,
    publishAtMillis = publishAtMillis,
    upload = when (status) {
        ClipStatus.QUEUED -> UploadState.Queued
        ClipStatus.PROCESSING -> UploadState.Processing(error ?: "Working", percent)
        ClipStatus.UPLOADING -> UploadState.Uploading(percent)
        ClipStatus.DONE -> UploadState.Done(videoId.orEmpty(), note = error)
        ClipStatus.FAILED -> UploadState.Failed(error ?: "Upload failed.")
        else -> UploadState.Idle
    },
    createdAt = createdAt,
    completedAt = completedAt
)
