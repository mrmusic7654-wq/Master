package com.mastercontrol.app.telegram.tdlib.core

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.UploadJob
import com.mastercontrol.app.domain.model.UploadProgressEvent
import com.mastercontrol.app.domain.model.UploadReceipt
import com.mastercontrol.app.telegram.tdlib.json.FileDto
import com.mastercontrol.app.telegram.tdlib.json.MessageDto
import com.mastercontrol.app.telegram.tdlib.json.TdContentParser
import com.mastercontrol.app.telegram.tdlib.json.TdRequests
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * One real Telegram upload driven by actual TDLib transfer state:
 *
 *  1. preliminaryUploadFile(inputFileGenerated) returns the file id
 *  2. TDLib asks for bytes (updateFileGenerationStart) -> we stream the content
 *     URI in 256 KiB chunks with writeGeneratedFilePart (never in RAM)
 *  3. when uploaded_size reaches the expected size the message is sent
 *     (sendMessage finalizes the preliminary upload)
 *  4. the sent message is re-verified with getMessage before completion
 *
 * Progress is derived from updateFile(uploaded_size) plus the bytes actually
 * written into TDLib; nothing is fabricated.
 */
internal class TdUploadSession(
    private val job: UploadJob,
    private val client: TdClientCore,
    private val contentResolver: ContentResolver,
    private val logger: Logger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var fileId: Int = 0
    @Volatile private var generationId: Long = 0L
    @Volatile private var uploadedBytes: Long = 0L
    @Volatile private var bytesWritten: Long = 0L
    @Volatile private var cancelled: Boolean = false

    private var totalBytes: Long = 0L
    private var progressSink: (suspend (UploadProgressEvent) -> Unit)? = null

    private val listeners = mutableListOf<Pair<String, (JsonObject) -> Unit>>()

    suspend fun run(onProgress: suspend (UploadProgressEvent) -> Unit): UploadReceipt {
        progressSink = onProgress
        totalBytes = resolveSize()
        onProgress(UploadProgressEvent.Preparing(totalBytes))

        installListeners()
        try {
            // 1. Register the generated file.
            val fileResponse = client.call(
                TdRequests.preliminaryUploadFile(
                    originalPath = "mc://${job.taskId}/${job.fileName}",
                    expectedSize = totalBytes,
                ),
            )
            fileId = runCatching { json.decodeFromJsonElement(FileDto.serializer(), fileResponse) }
                .getOrNull()?.id ?: 0
            if (fileId <= 0) throw AppError.TelegramFileError("TDLib did not assign a file id.")

            // 2. TDLib announces generation (asynchronously through the event
            //    loop); wait for it, then stream all parts from the content URI.
            waitUntil(timeoutMs = 60_000L) { generationId != 0L && !cancelled }
            if (cancelled) throw AppError.UploadCancelledError()
            writeAllParts()

            // 3. Wait until what we wrote has reached Telegram, pumping live
            //    progress while the remote side confirms receipt.
            val deadline = System.currentTimeMillis() + 30 * 60_000L
            while (uploadedBytes < totalBytes && !cancelled) {
                currentCoroutineContext().ensureActive()
                emitTransfer()
                if (System.currentTimeMillis() > deadline) {
                    throw AppError.TelegramFileError("Upload stalled while confirming transfer.")
                }
                delay(250)
            }
            if (cancelled) throw AppError.UploadCancelledError()
            logger.info(Tags.UPLOAD, "upload ${job.taskId} fully transferred ($totalBytes bytes)")

            // 4. Finalize: send the message referencing the uploaded file.
            onProgress(UploadProgressEvent.Verifying())
            val messageObj = client.call(
                TdRequests.sendMessageVideo(
                    chatId = job.channelId,
                    uploadedFileId = fileId,
                    caption = job.caption.orEmpty(),
                    durationSeconds = job.durationMs?.div(1000L),
                    width = job.width,
                    height = job.height,
                ),
            )
            val message = json.decodeFromJsonElement(MessageDto.serializer(), messageObj)
            if (message.id <= 0L) throw AppError.TelegramFileError("sendMessage returned no message id.")

            // 5. Verify through getMessage.
            val verification = TdMessageManager(client).verifyMessage(job.channelId, message.id)
            if (!verification.found || !verification.matchesVideo) {
                throw AppError.UploadFailedError(canRetry = false, detail = "Message ${message.id} could not be verified as a video.")
            }
            val ref = verification.ref
            return UploadReceipt(
                channelId = job.channelId,
                messageId = message.id,
                telegramFileId = ref?.telegramFileId ?: fileId,
                telegramRemoteFileId = ref?.remoteFileId.orEmpty(),
                telegramUniqueFileId = ref?.uniqueFileId.orEmpty(),
                fileSizeBytes = ref?.fileSizeBytes ?: totalBytes,
                mimeType = ref?.mimeType ?: job.mimeType,
            )
        } finally {
            uninstallListeners()
        }
    }

    fun cancel() {
        cancelled = true
        if (fileId > 0) {
            runCatching { client.call(TdRequests.cancelPreliminaryUploadFile(fileId)) }
        }
        if (generationId != 0L) {
            runCatching { client.call(TdRequests.finishFileGeneration(generationId, withError = true)) }
        }
    }

    // ---- internals ----------------------------------------------------------

    private suspend fun resolveSize(): Long {
        val uri = Uri.parse(job.sourceUri)
        job.totalBytes?.takeIf { it > 0L }?.let { return it }
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            if (afd.length > 0L) return afd.length
        }
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                val size = c.getLong(0)
                if (size > 0L) return size
            }
        }
        throw AppError.InvalidMediaError("Cannot determine the source file size.")
    }

    private fun installListeners() {
        val genStart: (JsonObject) -> Unit = { update ->
            val path = TdContentParser.primitiveText(update, "original_path").orEmpty()
            if (!path.startsWith("mc://${job.taskId}/")) return@let
            generationId = TdContentParser.primitiveLong(update, "generation_id") ?: 0L
        }
        val fileUpdate: (JsonObject) -> Unit = { update ->
            val fileObj = update["file"] as? JsonObject ?: return@let
            val id = TdContentParser.primitiveLong(fileObj, "id")?.toInt() ?: return@let
            if (id != fileId) return@let
            val remote = fileObj["remote"] as? JsonObject
            val uploaded = TdContentParser.primitiveLong(remote, "uploaded_size") ?: 0L
            if (uploaded > uploadedBytes) {
                // Non-suspend listener: only record real transfer state. Progress
                // is pumped to the sink from suspend loops in run()/writeChunks().
                uploadedBytes = uploaded
            }
        }
        client.onUpdate("updateFileGenerationStart", genStart)
        client.onUpdate("updateFile", fileUpdate)
        listeners += "updateFileGenerationStart" to genStart
        listeners += "updateFile" to fileUpdate
    }

    private fun uninstallListeners() {
        listeners.forEach { (type, handler) ->
            // TdClientCore keeps listeners; they short-circuit on task mismatch
            // and are cheap. Nothing further to remove by design.
        }
        listeners.clear()
    }

    private suspend fun writeAllParts() {
        val uri = Uri.parse(job.sourceUri)
        withContext(Dispatchers.IO) {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
                ?: throw AppError.MediaUnavailableError("Source file disappeared while uploading.")
            pfd.use { fd ->
                fd.openInputStream().use { input ->
                    writeChunks(input)
                }
            }
        }
        // Tell TDLib the generation completed successfully.
        client.call(TdRequests.finishFileGeneration(generationId, withError = false))
    }

    private suspend fun writeChunks(input: InputStream) {
        val buffer = ByteArray(CHUNK_SIZE)
        var offset = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            if (cancelled) throw AppError.UploadCancelledError()
            val read = withContext(Dispatchers.IO) { input.read(buffer) }
            if (read <= 0) break
            val b64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
            client.call(TdRequests.writeGeneratedFilePart(generationId, offset, b64))
            offset += read
            bytesWritten = offset
            emitTransfer()
            if (offset % PROGRESS_NOTIFY_INTERVAL < CHUNK_SIZE) {
                runCatching {
                    client.call(TdRequests.setFileGenerationProgress(generationId, totalBytes, offset))
                }
            }
        }
    }

    private suspend fun emitTransfer() {
        val reported = maxOf(bytesWritten, uploadedBytes).coerceAtMost(totalBytes)
        if (reported - lastReported >= PROGRESS_NOTIFY_INTERVAL || reported >= totalBytes) {
            lastReported = reported
            progressSink?.invoke(UploadProgressEvent.Transferring(reported, totalBytes))
        }
    }

    @Volatile private var lastReported: Long = 0L

    private suspend fun waitUntil(timeoutMs: Long?, condition: () -> Boolean) {
        val deadline = timeoutMs?.let { System.currentTimeMillis() + it }
        while (!condition()) {
            currentCoroutineContext().ensureActive()
            if (deadline != null && System.currentTimeMillis() > deadline) {
                throw AppError.TelegramFileError("Timed out waiting for TDLib.")
            }
            delay(150)
        }
    }

    companion object {
        private const val CHUNK_SIZE = 256 * 1024
        private const val PROGRESS_NOTIFY_INTERVAL = 2L * 1024 * 1024
    }
}
