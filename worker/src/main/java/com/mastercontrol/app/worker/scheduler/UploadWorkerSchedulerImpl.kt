package com.mastercontrol.app.worker.scheduler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mastercontrol.app.domain.port.UploadWorkerScheduler
import com.mastercontrol.app.worker.import.ImportFinalizeWorker
import com.mastercontrol.app.worker.reconcile.ReconciliationWorker
import com.mastercontrol.app.worker.upload.UploadQueueWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed [UploadWorkerScheduler].
 *
 * The upload queue is a *single* processor, so every scheduling call targets one
 * uniquely named worker: [ExistingWorkPolicy.APPEND_OR_REPLACE] replaces a
 * still-pending wake-up (no pile-up) and appends when a run is already active so
 * a completion wake-up is never lost.
 */
@Singleton
class UploadWorkerSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : UploadWorkerScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun scheduleUploadQueueProcessing(delayMs: Long) {
        val request = baseQueueRequest()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            UPLOAD_QUEUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    override fun scheduleImportFinalization(videoId: String) {
        val request = OneTimeWorkRequestBuilder<ImportFinalizeWorker>()
            .setInputData(ImportFinalizeWorker.inputData(videoId))
            .setConstraints(networkAwareConstraints())
            .build()
        workManager.enqueueUniqueWork(
            "$IMPORT_FINALIZE_PREFIX$videoId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun scheduleReconciliation(force: Boolean) {
        val request = OneTimeWorkRequestBuilder<ReconciliationWorker>()
            .setConstraints(networkAwareConstraints())
            .build()
        workManager.enqueueUniqueWork(
            RECONCILIATION_NAME,
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun baseQueueRequest(): OneTimeWorkRequest.Builder {
        return OneTimeWorkRequestBuilder<UploadQueueWorker>()
            .setConstraints(networkAwareConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.SECONDS)
    }

    private fun networkAwareConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    companion object {
        const val UPLOAD_QUEUE_NAME = "upload-queue-processor"
        const val IMPORT_FINALIZE_PREFIX = "import-finalize-"
        const val RECONCILIATION_NAME = "reconciliation"
    }
}
