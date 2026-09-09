package com.mastercontrol.app.domain.port

import com.mastercontrol.app.domain.model.UploadJob
import com.mastercontrol.app.domain.model.UploadProgressEvent
import kotlinx.coroutines.flow.Flow

/**
 * Executes one real Telegram upload. Implemented by the telegram module over
 * TDLib; the worker module drives it. Progress events are emitted from actual
 * transfer state, never fabricated. The flow completes after a terminal event
 * (Completed/Failed) or when cancelled.
 */
interface TelegramUploadExecutor {
    fun upload(job: UploadJob): Flow<UploadProgressEvent>

    /** Cancels an in-flight upload started with the same [taskId] (best effort). */
    fun cancel(taskId: Long)
}

/**
 * Schedules durable background work. Implemented over WorkManager in the
 * worker module. The upload engine is a single queue processor that reclaims
 * interrupted tasks from the database, so scheduling is idempotent.
 */
interface UploadWorkerScheduler {
    /** Schedules (or nudges) the single upload-queue processor run. */
    fun scheduleUploadQueueProcessing(delayMs: Long = 0L)
    fun scheduleImportFinalization(videoId: String)
    fun scheduleReconciliation(force: Boolean = false)
}
