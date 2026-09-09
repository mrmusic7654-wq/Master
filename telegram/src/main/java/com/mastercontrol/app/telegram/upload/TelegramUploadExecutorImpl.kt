package com.mastercontrol.app.telegram.upload

import android.content.ContentResolver
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.UploadJob
import com.mastercontrol.app.domain.model.UploadProgressEvent
import com.mastercontrol.app.domain.port.TelegramUploadExecutor
import com.mastercontrol.app.telegram.tdlib.core.TdUploadSession
import com.mastercontrol.app.telegram.tdlib.manager.TdLibManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Domain-port implementation: executes [UploadJob]s through real TDLib
 * transfers and exposes them as [UploadProgressEvent] flows.
 */
@Singleton
class TelegramUploadExecutorImpl @Inject constructor(
    private val manager: TdLibManager,
    private val contentResolver: ContentResolver,
    private val logger: Logger,
) : TelegramUploadExecutor {

    private val activeSessions = ConcurrentHashMap<Long, TdUploadSession>()

    override fun upload(job: UploadJob): Flow<UploadProgressEvent> = flow {
        ensureEngineStarted()
        val session = TdUploadSession(job, manager.client, contentResolver, logger)
        activeSessions[job.taskId] = session
        try {
            val receipt = session.run { event -> emit(event) }
            emit(UploadProgressEvent.Completed(receipt))
            logger.info(Tags.UPLOAD, "upload ${job.taskId} completed -> message ${receipt.messageId}")
        } catch (t: AppError) {
            logger.warn(Tags.UPLOAD, "upload ${job.taskId} failed: ${t.userMessage}")
            emit(UploadProgressEvent.Failed(t))
        } catch (t: Throwable) {
            logger.error(Tags.UPLOAD, "upload ${job.taskId} crashed", t)
            emit(UploadProgressEvent.Failed(AppError.UploadFailedError(detail = t.message ?: "unknown error")))
        } finally {
            activeSessions.remove(job.taskId)
        }
    }

    override fun cancel(taskId: Long) {
        activeSessions.remove(taskId)?.cancel()
    }

    private suspend fun ensureEngineStarted() {
        manager.start()
        val state = manager.authManager.authorizationState.value
        if (state !is com.mastercontrol.app.domain.model.AuthorizationState.Ready) {
            throw AppError.TelegramAuthenticationError("Telegram session is not ready (state: ${state::class.simpleName}).")
        }
    }
}
