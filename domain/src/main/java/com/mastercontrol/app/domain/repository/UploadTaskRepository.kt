package com.mastercontrol.app.domain.repository

import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import kotlinx.coroutines.flow.Flow

/** Durable upload queue persistence (Room-backed; survives restarts). */
interface UploadTaskRepository {

    fun observeAll(): Flow<List<UploadTask>>

    fun observeByState(state: UploadTaskState): Flow<List<UploadTask>>

    fun observeForVideo(videoId: String): Flow<List<UploadTask>>

    suspend fun getTask(taskId: Long): UploadTask?

    suspend fun getTaskByVideo(videoId: String): UploadTask?

    /** Insert a new queued task and return it with its row id. */
    suspend fun enqueue(task: UploadTask): UploadTask

    suspend fun update(task: UploadTask)

    suspend fun delete(taskId: Long)

    suspend fun deleteAll(onlyStates: Set<UploadTaskState>? = null): Int

    /** Oldest queued task (FIFO), if any. */
    suspend fun nextQueued(): UploadTask?

    /** Returns the active task (QUEUED..VERIFYING/RETRYING) for [videoId], if any. */
    suspend fun getActiveTaskForVideo(videoId: String): UploadTask?

    /** Moves FAILED tasks back to QUEUED; returns how many were requeued. */
    suspend fun requeueAllFailed(): Int

    /** Reclaims tasks interrupted by process death (in-flight older than [graceMs]). */
    suspend fun reclaimInFlightTasksOlderThan(graceMs: Long): Int

    suspend fun countByState(state: UploadTaskState): Int

    suspend fun countAll(): Long
}
