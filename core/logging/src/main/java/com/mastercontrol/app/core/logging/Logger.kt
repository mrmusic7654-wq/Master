package com.mastercontrol.app.core.logging

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/**
 * Central logging façade. All modules log through [Logger]; nobody touches
 * android.util.Log directly.
 *
 * Security contract (enforced by construction + [MasterLogger.registerSecret]):
 *  - never log api_hash, phone verification codes, 2FA passwords, session keys
 *  - never log file contents or full content URIs
 *  - release builds disable verbose/debug network logs
 */
interface Logger {
    fun verbose(tag: String, message: String)
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)

    /** Current in-memory diagnostics buffer (never contains secrets). */
    fun exportDiagnostics(): String
}

object Tags {
    const val APP = "MasterControl"
    const val DB = "MC-Database"
    const val TELEGRAM = "MC-Telegram"
    const val AUTH = "MC-Auth"
    const val UPLOAD = "MC-Upload"
    const val IMPORT = "MC-Import"
    const val WORKER = "MC-Worker"
    const val NETWORK = "MC-Network"
    const val SECURITY = "MC-Security"
    const val UI = "MC-UI"
    const val SETTINGS = "MC-Settings"
}
