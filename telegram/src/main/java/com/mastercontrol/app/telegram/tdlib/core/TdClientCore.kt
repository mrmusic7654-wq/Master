package com.mastercontrol.app.telegram.tdlib.core

import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.telegram.tdlib.jni.TdJsonJni
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Single controlled TDLib JSON client.
 *
 * - One receive thread drains td_json_client_receive (mandatory single-thread).
 * - Raw JSON payloads are queued to a single-threaded event loop that matches
 *   request responses via "@extra" and fans updates out to listeners.
 * - UI/domain code never talks to this class directly; it goes through the
 *   repositories (TelegramAccountRepository / TelegramChannelRepository /
 *   TelegramMediaRepository).
 */
class TdClientCore internal constructor(
    private val logger: Logger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var running = false

    private val receiveQueue = Channel<String>(Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<String, kotlinx.coroutines.CancellableContinuation<JsonObject>>()
    private val extraCounter = AtomicLong(0L)
    private val listeners = ConcurrentHashMap<String, MutableList<(JsonObject) -> Unit>>()
    private var eventScope: CoroutineScope? = null

    val isRunning: Boolean get() = running

    /** Installs a listener for one update type (e.g. "updateAuthorizationState"). */
    fun onUpdate(type: String, handler: (JsonObject) -> Unit) {
        listeners.computeIfAbsent(type) { mutableListOf() }.add(handler)
    }

    /** Synchronous engine requests (TDLib "execute"): returns parsed JSON or throws. */
    fun executeSync(request: JsonObject): JsonObject {
        ensureHandle()
        val raw = TdJsonJni.nativeExecute(handle, request.toString())
        if (raw == null) throw AppError.TelegramConnectionError("TDLib execute returned nothing.")
        val obj = json.parseToJsonElement(raw).jsonObject
        throwIfError(obj)
        return obj
    }

    /** Asynchronous request with response correlation. Cancellation-safe. */
    suspend fun call(request: JsonObject): JsonObject {
        ensureHandle()
        val extra = "mc-${extraCounter.incrementAndGet()}"
        val withExtra = JsonObject(request.toMutableMap().apply { this["@extra"] = JsonPrimitive(extra) })
        val result = suspendCancellableCoroutine<JsonObject> { cont ->
            pending[extra] = cont
            cont.invokeOnCancellation { pending.remove(extra) }
            TdJsonJni.nativeSend(handle, withExtra.toString())
        }
        throwIfError(result)
        return result
    }

    fun start(scope: CoroutineScope) {
        if (running) return
        val newHandle = TdJsonJni.nativeCreate()
        if (newHandle == 0L) {
            throw AppError.TelegramConnectionError(
                "TDLib native library missing. Build the native libraries with scripts/build-tdlib.sh first.",
            )
        }
        handle = newHandle
        running = true
        eventScope = scope
        Thread({ receiveLoop() }, "tdlib-receive").apply { isDaemon = true }.start()
        scope.launch { eventDispatchLoop() }
    }

    fun stop() {
        running = false
        if (handle != 0L) {
            runCatching { TdJsonJni.nativeDestroy(handle) }
            handle = 0L
        }
    }

    fun destroy() = stop()

    private fun ensureHandle() {
        if (handle == 0L) {
            throw AppError.TelegramConnectionError("TDLib is not running. Start the Telegram engine first.")
        }
    }

    private fun receiveLoop() {
        while (running) {
            try {
                val raw = TdJsonJni.nativeReceive(handle, 0.25) ?: continue
                receiveQueue.trySend(raw)
            } catch (t: Throwable) {
                if (running) logger.error(Tags.TELEGRAM, "td receive error: ${t.message}", t)
            }
        }
    }

    private suspend fun eventDispatchLoop() {
        for (raw in receiveQueue) {
            try {
                val obj = json.parseToJsonElement(raw).jsonObject
                val type = obj["@type"]?.jsonPrimitive?.contentOrNull ?: continue
                val extra = obj["@extra"]?.jsonPrimitive?.contentOrNull
                if (extra != null) {
                    pending.remove(extra)?.let { cont ->
                        if (cont.isActive) cont.resume(obj)
                    }
                } else {
                    listeners[type]?.toList()?.forEach { handler ->
                        runCatching { handler(obj) }.onFailure { t ->
                            logger.warn(Tags.TELEGRAM, "update handler '$type' failed: ${t.message}", t)
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.warn(Tags.TELEGRAM, "event dispatch error: ${t.message}", t)
            }
        }
    }

    private fun throwIfError(obj: JsonObject) {
        val type = obj["@type"]?.jsonPrimitive?.contentOrNull
        if (type == "error") {
            throw TdErrorMapper.map(obj)
        }
    }
}
