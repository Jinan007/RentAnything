package com.clippost.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Edits (title, caption...) and upload status are saved by separate queries on purpose,
 * so the background worker and the screen can never overwrite each other's fields.
 */
@Dao
interface ClipDao {

    @Query("SELECT * FROM clips ORDER BY createdAt ASC")
    suspend fun getAll(): List<ClipEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ClipEntity)

    @Query(
        "UPDATE clips SET title = :title, caption = :caption, privacy = :privacy, " +
            "addShorts = :addShorts, captions = :captions, vertical = :vertical, facecam = :facecam, " +
            "topText = :topText, publishAtMillis = :publishAtMillis WHERE id = :id"
    )
    suspend fun updateEdits(
        id: String,
        title: String,
        caption: String,
        privacy: String,
        addShorts: Boolean,
        captions: Boolean,
        vertical: Boolean,
        facecam: String,
        topText: String,
        publishAtMillis: Long?
    )

    @Query(
        "UPDATE clips SET status = :status, percent = :percent, videoId = :videoId, " +
            "error = :error, completedAt = :completedAt WHERE id = :id"
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        percent: Int,
        videoId: String?,
        error: String?,
        completedAt: Long?
    )

    @Query("UPDATE clips SET workId = :workId WHERE id = :id")
    suspend fun setWorkId(id: String, workId: String?)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun delete(id: String)
}
