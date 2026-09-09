package com.mastercontrol.app.worker.reconcile

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.usecase.ReconcileUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Runs the reconciliation engine: verifies every locally recorded Telegram
 * mapping and optionally scans the default channel for unmanaged media.
 * Scheduled on demand (or periodically by app-level wiring); never auto-deletes.
 */
@HiltWorker
class ReconciliationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconcile: ReconcileUseCase,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val report = reconcile(scanUnmanagedWindow = UNMANAGED_SCAN_WINDOW)
            logger.info(
                Tags.WORKER,
                "reconciliation: ${report.mappingsChecked} checked, ${report.mappingsOk} ok, " +
                    "${report.mappingsStale} stale, ${report.mappingsRemoteMissing} remote-missing, " +
                    "${report.unmanagedFound} unmanaged",
            )
            Result.success()
        } catch (t: CancellationException) {
            throw t
        } catch (t: AppError) {
            // Transient network/auth failures should not wedge future runs;
            // the periodic schedule will try again.
            logger.warn(Tags.WORKER, "reconciliation failed: ${t.userMessage}")
            Result.retry()
        } catch (t: Throwable) {
            logger.error(Tags.WORKER, "reconciliation crashed", t)
            Result.retry()
        }
    }

    companion object {
        /** How many of the most recent channel messages are scanned for unmanaged media. */
        const val UNMANAGED_SCAN_WINDOW = 200
    }
}
