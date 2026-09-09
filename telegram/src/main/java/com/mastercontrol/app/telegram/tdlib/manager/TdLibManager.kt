package com.mastercontrol.app.telegram.tdlib.manager

import android.content.Context
import android.os.Build
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import com.mastercontrol.app.core.security.TdlibEncryptionKeyProvider
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import com.mastercontrol.app.telegram.tdlib.core.TdAuthManager
import com.mastercontrol.app.telegram.tdlib.core.TdChatManager
import com.mastercontrol.app.telegram.tdlib.core.TdClientCore
import com.mastercontrol.app.telegram.tdlib.core.TdMessageManager
import com.mastercontrol.app.telegram.tdlib.jni.TdJsonJni
import com.mastercontrol.app.telegram.tdlib.jni.TdNativeLogSink
import com.mastercontrol.app.telegram.tdlib.json.TdRequests
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owns the single controlled TDLib client and its satellites. This is the only
 * place that understands engine lifecycle; everything else goes through the
 * repositories.
 */
@Singleton
class TdLibManager @Inject constructor(
    @androidx.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val credentialsRepository: TelegramCredentialsRepository,
    private val keyProvider: TdlibEncryptionKeyProvider,
    private val logger: Logger,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val client = TdClientCore(logger)
    val authManager: TdAuthManager by lazy { TdAuthManager(client) }
    val chatManager: TdChatManager by lazy { TdChatManager(client) }
    val messageManager: TdMessageManager by lazy { TdMessageManager(client) }

    @Volatile
    private var engineStarted = false

    /** Starts the engine if credentials are stored; safe to call repeatedly. */
    suspend fun start() {
        if (engineStarted) return
        if (!credentialsRepository.isConfigured()) return

        installLogBridge()
        client.start(scope)
        engineStarted = true

        val dbDir = File(context.filesDir, "tdlib_db").apply { mkdirs() }.absolutePath
        val filesDir = File(context.filesDir, "tdlib_files").apply { mkdirs() }.absolutePath
        val creds = credentialsRepository.readCredentials()
            ?: return

        val params = TdRequests.setTdlibParameters(
            databaseDirectory = dbDir,
            filesDirectory = filesDir,
            databaseEncryptionKeyB64 = keyProvider.getOrCreate(),
            apiId = creds.first,
            apiHash = creds.second,
            systemLanguageCode = Locale.getDefault().language,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            systemVersion = Build.VERSION.RELEASE,
            applicationVersion = appVersion(),
        )
        client.call(params)
        runCatching { client.call(TdRequests.setLogVerbosityLevel(logVerbosity)) }
        logger.info(Tags.TELEGRAM, "TDLib engine started")
    }

    suspend fun restart() {
        stop()
        start()
    }

    fun stop() {
        if (!engineStarted) return
        runCatching { client.stop() }
        engineStarted = false
    }

    private fun installLogBridge() {
        TdNativeLogSink.onLog = { level, message ->
            when {
                level <= 1 -> logger.warn(Tags.TELEGRAM, message)
                level <= 3 -> logger.debug(Tags.TELEGRAM, message)
                else -> logger.verbose(Tags.TELEGRAM, message)
            }
        }
    }

    private fun appVersion(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")

    private val logVerbosity: Int
        get() = if (BuildConfig.DEBUG) 2 else 1
}
