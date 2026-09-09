package com.mastercontrol.app.core.logging

import android.util.Log
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/** Writes a message to logcat and (optionally) keeps it in a bounded ring buffer. */
@Singleton
class MasterLogger @Inject constructor(
    private val configuration: LoggerConfiguration,
) : Logger {

    private data class Entry(
        val time: Instant,
        val level: LogLevel,
        val tag: String,
        val message: String,
    )

    private val buffer = ArrayDeque<Entry>()

    @Synchronized
    private fun record(level: LogLevel, tag: String, message: String) {
        val redacted = redactor.redact(message)
        when (level) {
            LogLevel.VERBOSE -> if (configuration.debugLogsEnabled) Log.v(tag, redacted)
            LogLevel.DEBUG -> if (configuration.debugLogsEnabled) Log.d(tag, redacted)
            LogLevel.INFO -> Log.i(tag, redacted)
            LogLevel.WARN -> Log.w(tag, redacted)
            LogLevel.ERROR -> Log.e(tag, redacted)
        }
        if (configuration.bufferEnabled && level != LogLevel.VERBOSE) {
            buffer.addLast(Entry(Instant.now(), level, tag, redacted))
            while (buffer.size > configuration.bufferSize) buffer.removeFirst()
        }
    }

    override fun verbose(tag: String, message: String) = record(LogLevel.VERBOSE, tag, message)
    override fun debug(tag: String, message: String) = record(LogLevel.DEBUG, tag, message)
    override fun info(tag: String, message: String) = record(LogLevel.INFO, tag, message)

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        record(LogLevel.WARN, tag, message)
        throwable?.let { if (configuration.debugLogsEnabled) Log.w(tag, it) }
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        record(LogLevel.ERROR, tag, message)
        throwable?.let { Log.e(tag, message, it) }
    }

    @Synchronized
    override fun exportDiagnostics(): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        return buildString {
            buffer.forEach { e ->
                append(fmt.format(e.time))
                append("  ")
                append(e.level.name.padEnd(5))
                append(' ')
                append(e.tag)
                append("  ")
                append(e.message)
                append('\n')
            }
        }
    }

    /** Registers a value that must never appear in logs (e.g. the api hash). */
    @Synchronized
    fun registerSecret(value: String) {
        if (value.length >= 8) redactor.add(value)
    }

    private val redactor = SecretsRedactor()

    private class SecretsRedactor {
        private val secrets = mutableListOf<String>()

        @Synchronized
        fun add(value: String) {
            if (secrets.none { it == value }) secrets.add(value)
        }

        @Synchronized
        fun redact(input: String): String {
            var out = input
            for (secret in secrets) {
                if (secret.isNotEmpty()) out = out.replace(secret, "***")
            }
            return out
        }
    }
}

/** Runtime logger policy. */
data class LoggerConfiguration(
    val debugLogsEnabled: Boolean,
    val bufferEnabled: Boolean = true,
    val bufferSize: Int = 1000,
)
