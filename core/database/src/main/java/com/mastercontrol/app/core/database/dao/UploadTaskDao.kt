package com.mastercontrol.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mastercontrol.app.core.database.entity.UploadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadTaskDao {

    @Query("SELECT * FROM upload_tasks ORDER BY CASE state WHEN 'QUEUED' THEN 0 WHEN 'UPLOADING' THEN 1 WHEN 'VERIFYING' THEN 2 WHEN 'RETRYING' THEN 3 WHEN 'PREPARING' THEN 4 WHEN 'FAILED' THEN 5 WHEN 'CANCELLED' THEN 6 ELSE 7 END ASC, createdAtEpochMs ASC")
    fun observeAll(): Flow<List<UploadTaskEntity>>

    @Query("SELECT * FROM upload_tasks WHERE state = :state ORDER BY createdAtEpochMs ASC")
    fun observeByState(state: String): Flow<List<UploadTaskEntity>>

    @Query("SELECT * FROM upload_tasks WHERE videoId = :videoId ORDER BY taskId DESC")
    fun observeForVideo(videoId: String): Flow<List<UploadTaskEntity>>

    @Query("SELECT * FROM upload_tasks WHERE state = 'QUEUED' ORDER BY createdAtEpochMs ASC, taskId ASC LIMIT 1")
    suspend fun nextQueued(): UploadTaskEntity?

    @Query("SELECT * FROM upload_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun get(taskId: Long): UploadTaskEntity?

    @Query("SELECT * FROM upload_tasks WHERE videoId = :videoId ORDER BY taskId DESC LIMIT 1")
    suspend fun getByVideo(videoId: String): UploadTaskEntity?

    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE videoId = :videoId AND state IN ('QUEUED', 'PREPARING', 'UPLOADING', 'VERIFYING', 'RETRYING')
        ORDER BY taskId ASC LIMIT 1
        """,
    )
    suspend fun getActiveForVideo(videoId: String): UploadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: UploadTaskEntity): Long

    @Update
    suspend fun update(task: UploadTaskEntity)

    @Query("DELETE FROM upload_tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: Long)

    @Query("DELETE FROM upload_tasks WHERE state IN (:states)")
    suspend fun deleteByStates(states: List<String>): Int

    @Query("UPDATE upload_tasks SET state = 'QUEUED', lastError = NULL, updatedAtEpochMs = :updatedAtEpochMs WHERE state = 'FAILED'")
    suspend fun requeueFailed(updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE upload_tasks SET state = 'QUEUED', updatedAtEpochMs = :updatedAtEpochMs
        WHERE state IN ('PREPARING', 'UPLOADING', 'VERIFYING') AND updatedAtEpochMs < :cutoffEpochMs
        """,
    )
    suspend fun reclaimInFlight(cutoffEpochMs: Long, updatedAtEpochMs: Long): Int

    @Query("SELECT COUNT(*) FROM upload_tasks WHERE state = :state")
    suspend fun countByState(state: String): Int

    @Query("SELECT COUNT(*) FROM upload_tasks")
    suspend fun countAll(): Long

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM upload_tasks WHERE state IN ('QUEUED', 'PREPARING', 'UPLOADING', 'RETRYING')")
    suspend fun sumPendingBytes(): Long
}
