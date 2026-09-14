package com.mastercontrol.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.core.common.text.SecretMasking
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.AppSettings
import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.ImportMode
import com.mastercontrol.app.domain.model.ImportPreview
import com.mastercontrol.app.domain.model.LibraryLayout
import com.mastercontrol.app.domain.model.LibrarySort
import com.mastercontrol.app.domain.model.ReconciliationReport
import com.mastercontrol.app.domain.model.TelegramAccountInfo
import com.mastercontrol.app.domain.model.ThemeMode
import com.mastercontrol.app.domain.model.UploadNetworkRule
import com.mastercontrol.app.domain.port.DocumentStore
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.AppLockMode
import com.mastercontrol.app.domain.repository.AppLockRepository
import com.mastercontrol.app.domain.repository.SettingsRepository
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import com.mastercontrol.app.domain.usecase.ExportCatalogUseCase
import com.mastercontrol.app.domain.usecase.ImportCatalogUseCase
import com.mastercontrol.app.domain.usecase.PreviewCatalogImportUseCase
import com.mastercontrol.app.domain.usecase.ReconcileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** PIN editor state; the current PIN is required when a PIN lock is already active. */
data class PinEditor(
    val currentPin: String = "",
    val newPin: String = "",
    val confirmPin: String = "",
    val requiresCurrent: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
)

/** Real catalog counters shown in diagnostics. */
data class DiagnosticCounts(
    val videos: Long = 0L,
    val problematicMappings: Long = 0L,
    val activityEntries: Long = 0L,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val lockEnabled: Boolean = false,
    val lockMode: AppLockMode = AppLockMode.PIN,
    val authorizationState: AuthorizationState = AuthorizationState.NeedsConfiguration,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val accountInfo: TelegramAccountInfo? = null,
    val tdlibVersion: String? = null,
    val credentialsConfigured: Boolean = false,
    val maskedApiId: String? = null,
    val maskedApiHash: String? = null,
    val counts: DiagnosticCounts = DiagnosticCounts(),
    val lastReconciliation: ReconciliationReport? = null,
    val exportFileName: String = "",
    val busy: Boolean = false,
    val busyLabel: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pinEditor: PinEditor? = null,
    val lockModePickerOpen: Boolean = false,
    val confirmDisableLock: Boolean = false,
    val confirmLogout: Boolean = false,
    val confirmRestartEngine: Boolean = false,
    val confirmClearSecrets: Boolean = false,
    val importPreview: ImportPreview? = null,
    val importMode: ImportMode = ImportMode.MERGE,
    val confirmImport: Boolean = false,
    val loggedOut: Boolean = false,
    val secretsCleared: Boolean = false,
) {
    val isAuthenticated: Boolean get() = authorizationState is AuthorizationState.Ready
}

/**
 * Settings, security, backup and diagnostics.
 *
 * Secrets are only ever displayed masked, and the destructive actions (logout,
 * clearing secrets, importing over an existing catalog) require an explicit
 * acknowledgement that names what is lost.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository,
    private val accountRepository: TelegramAccountRepository,
    private val credentialsRepository: TelegramCredentialsRepository,
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
    private val documentStore: DocumentStore,
    private val exportCatalog: ExportCatalogUseCase,
    private val previewCatalogImport: PreviewCatalogImportUseCase,
    private val importCatalog: ImportCatalogUseCase,
    private val reconcile: ReconcileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Backup text held between preview and the confirmed import. */
    private var pendingImportJson: String? = null

    init {
        _uiState.update { it.copy(exportFileName = documentStore.suggestedExportName()) }
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            appLockRepository.observeLockEnabled().collect { enabled ->
                _uiState.update { it.copy(lockEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appLockRepository.observeLockMode().collect { mode ->
                _uiState.update { it.copy(lockMode = mode) }
            }
        }
        viewModelScope.launch {
            accountRepository.observeAuthorizationState().collect { state ->
                _uiState.update { it.copy(authorizationState = state) }
                refreshAccountInfo()
            }
        }
        viewModelScope.launch {
            accountRepository.observeConnectionState().collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            credentialsRepository.observeConfigured().collect { configured ->
                _uiState.update { it.copy(credentialsConfigured = configured) }
                refreshMaskedCredentials()
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(tdlibVersion = runCatching { accountRepository.getTdlibVersion() }.getOrNull()) }
            refreshDiagnostics()
        }
    }

    private suspend fun refreshAccountInfo() {
        val info = runCatching { accountRepository.getAccountInfo() }.getOrNull()
        _uiState.update { it.copy(accountInfo = info) }
    }

    private suspend fun refreshMaskedCredentials() {
        val credentials = runCatching { credentialsRepository.readCredentials() }.getOrNull()
        _uiState.update {
            it.copy(
                maskedApiId = credentials?.first?.let { id -> SecretMasking.maskSecret(id.toString(), visibleTail = 2) },
                maskedApiHash = credentials?.second?.let { hash -> SecretMasking.maskSecret(hash, visibleTail = 4) },
            )
        }
    }

    private suspend fun refreshDiagnostics() {
        val videos = runCatching { videoRepository.countVideos() }.getOrDefault(0L)
        val problematic = runCatching { videoRepository.countProblematicMappings() }.getOrDefault(0L)
        val entries = runCatching { activityRepository.count() }.getOrDefault(0L)
        _uiState.update {
            it.copy(counts = DiagnosticCounts(videos = videos, problematicMappings = problematic, activityEntries = entries))
        }
    }

    fun onRefreshDiagnostics() {
        runGuarded("Diagnostics could not be refreshed.") {
            refreshDiagnostics()
            refreshMaskedCredentials()
            _uiState.update { it.copy(tdlibVersion = runCatching { accountRepository.getTdlibVersion() }.getOrNull()) }
        }
    }

    // ---- appearance & library defaults ------------------------------------

    fun onThemeModeChange(mode: ThemeMode) = applySetting("The theme could not be changed.") {
        settingsRepository.setThemeMode(mode)
    }

    fun onDynamicColorChange(enabled: Boolean) = applySetting("Dynamic color could not be changed.") {
        settingsRepository.setDynamicColor(enabled)
    }

    fun onLibraryLayoutChange(layout: LibraryLayout) = applySetting("The library layout could not be changed.") {
        settingsRepository.setLibraryLayout(layout)
    }

    fun onLibrarySortChange(sort: LibrarySort) = applySetting("The default sort order could not be changed.") {
        settingsRepository.setLibrarySort(sort)
    }

    // ---- uploads ----------------------------------------------------------

    fun onUploadNetworkRuleChange(rule: UploadNetworkRule) = applySetting("The network rule could not be changed.") {
        settingsRepository.setUploadNetworkRule(rule)
    }

    fun onChargingOnlyChange(enabled: Boolean) = applySetting("The charging rule could not be changed.") {
        settingsRepository.setAllowUploadsOnlyWhileCharging(enabled)
    }

    fun onMaxConcurrentUploadsChange(count: Int) = applySetting("The concurrency limit could not be changed.") {
        settingsRepository.setMaxConcurrentUploads(count.coerceIn(1, MAX_CONCURRENT_UPLOADS))
    }

    fun onMediaHashingChange(enabled: Boolean) = applySetting("Checksum capture could not be changed.") {
        settingsRepository.setMediaHashingEnabled(enabled)
    }

    fun onThumbnailQualityChange(standard: Boolean) = applySetting("Thumbnail quality could not be changed.") {
        settingsRepository.setThumbnailQualityEnabledStandard(standard)
    }

    fun onThumbnailCaptureChange(ms: Long) = applySetting("The poster frame offset could not be changed.") {
        settingsRepository.setThumbnailCaptureMs(ms)
    }

    fun onCaptionVideoIdChange(enabled: Boolean) = applySetting("The caption rule could not be changed.") {
        settingsRepository.setCaptionIncludesVideoId(enabled)
    }

    fun onAutoDeleteLocalCopyChange(enabled: Boolean) = applySetting("The cleanup rule could not be changed.") {
        settingsRepository.setAutoDeleteLocalCopyAfterUpload(enabled)
    }

    // ---- app lock ---------------------------------------------------------

    fun onSetPinClick() {
        val requiresCurrent = _uiState.value.lockEnabled && _uiState.value.lockMode == AppLockMode.PIN
        _uiState.update { it.copy(pinEditor = PinEditor(requiresCurrent = requiresCurrent)) }
    }

    fun onPinEditorChange(transform: (PinEditor) -> PinEditor) =
        _uiState.update { state -> state.copy(pinEditor = state.pinEditor?.let(transform)) }

    fun onDismissPinEditor() = _uiState.update { it.copy(pinEditor = null) }

    fun onSavePin() {
        val editor = _uiState.value.pinEditor ?: return
        if (editor.requiresCurrent && editor.currentPin.isEmpty()) {
            onPinEditorChange { it.copy(error = "Enter your current PIN first.") }
            return
        }
        if (editor.newPin.length < MIN_PIN_LENGTH || !editor.newPin.all(Char::isDigit)) {
            onPinEditorChange { it.copy(error = "The new PIN must be at least $MIN_PIN_LENGTH digits.") }
            return
        }
        if (editor.newPin != editor.confirmPin) {
            onPinEditorChange { it.copy(error = "The two PINs do not match.") }
            return
        }
        runGuarded("The PIN could not be saved.") {
            if (editor.requiresCurrent && !appLockRepository.verifyPin(editor.currentPin)) {
                _uiState.update { state ->
                    state.copy(pinEditor = state.pinEditor?.copy(error = "The current PIN is not correct.", saving = false))
                }
                return@runGuarded
            }
            appLockRepository.setPin(editor.newPin)
            activityRepository.add(
                ActivityLogEntry(
                    type = ActivityType.APP_LOCK_ENABLED,
                    message = "App lock PIN was set",
                    createdAt = Instant.now(),
                ),
            )
            _uiState.update { it.copy(pinEditor = null, infoMessage = "App lock PIN saved. It is stored as a Keystore-protected hash." ) }
        }
    }

    fun onLockModePickerOpen() = _uiState.update { it.copy(lockModePickerOpen = true) }

    fun onLockModePickerDismiss() = _uiState.update { it.copy(lockModePickerOpen = false) }

    fun onLockModeSelected(mode: AppLockMode) {
        _uiState.update { it.copy(lockModePickerOpen = false) }
        if (mode == AppLockMode.PIN && !_uiState.value.lockEnabled) {
            onSetPinClick()
            return
        }
        runGuarded("The unlock method could not be changed.") {
            appLockRepository.setMode(mode)
            _uiState.update { it.copy(infoMessage = "Unlock method set to ${mode.label()}.") }
        }
    }

    fun onDisableLockClick() = _uiState.update { it.copy(confirmDisableLock = true) }

    fun onDismissDisableLock() = _uiState.update { it.copy(confirmDisableLock = false) }

    fun onConfirmDisableLock() {
        runGuarded("The app lock could not be turned off.") {
            appLockRepository.disable()
            activityRepository.add(
                ActivityLogEntry(
                    type = ActivityType.APP_LOCK_DISABLED,
                    message = "App lock was turned off",
                    createdAt = Instant.now(),
                ),
            )
            _uiState.update { it.copy(confirmDisableLock = false, infoMessage = "App lock is off.") }
        }
    }

    // ---- Telegram account -------------------------------------------------

    fun onLogoutClick() = _uiState.update { it.copy(confirmLogout = true) }

    fun onDismissLogout() = _uiState.update { it.copy(confirmLogout = false) }

    fun onConfirmLogout() {
        runGuarded("Telegram could not be signed out.") {
            accountRepository.logout()
            activityRepository.add(
                ActivityLogEntry(
                    type = ActivityType.LOGGED_OUT,
                    message = "Signed out of Telegram on this device",
                    createdAt = Instant.now(),
                ),
            )
            _uiState.update { it.copy(confirmLogout = false, loggedOut = true) }
        }
    }

    fun onRestartEngineClick() = _uiState.update { it.copy(confirmRestartEngine = true) }

    fun onDismissRestartEngine() = _uiState.update { it.copy(confirmRestartEngine = false) }

    fun onConfirmRestartEngine() {
        runGuarded("The Telegram engine could not be restarted.") {
            accountRepository.restart()
            _uiState.update {
                it.copy(
                    confirmRestartEngine = false,
                    infoMessage = "The Telegram engine is restarting. Uploads in the queue resume on their own.",
                )
            }
        }
    }

    fun onClearSecretsClick() = _uiState.update { it.copy(confirmClearSecrets = true) }

    fun onDismissClearSecrets() = _uiState.update { it.copy(confirmClearSecrets = false) }

    fun onConfirmClearSecrets() {
        runGuarded("Stored secrets could not be cleared.") {
            credentialsRepository.clear()
            appLockRepository.disable()
            activityRepository.add(
                ActivityLogEntry(
                    type = ActivityType.CREDENTIALS_UPDATED,
                    message = "Stored Telegram credentials and app lock secret were cleared",
                    createdAt = Instant.now(),
                ),
            )
            _uiState.update { it.copy(confirmClearSecrets = false, secretsCleared = true) }
        }
    }

    // ---- backup -----------------------------------------------------------

    /** Called with the SAF destination the operator created. */
    fun onExportDestinationPicked(uri: String) {
        if (uri.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No destination was chosen.") }
            return
        }
        runGuarded("The catalog could not be exported.") {
            val json = exportCatalog()
            documentStore.writeText(uri, json)
            activityRepository.add(
                ActivityLogEntry(
                    type = ActivityType.BACKUP_EXPORTED,
                    message = "Catalog exported (${json.length} characters)",
                    createdAt = Instant.now(),
                ),
            )
            _uiState.update {
                it.copy(
                    infoMessage = "Catalog exported. It contains IDs, metadata and Telegram mappings — " +
                        "no API credentials and no media.",
                    exportFileName = documentStore.suggestedExportName(),
                )
            }
        }
    }

    /** Called with the SAF file the operator picked for import. */
    fun onImportFilePicked(uri: String) {
        if (uri.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No backup file was chosen.") }
            return
        }
        runGuarded("The backup could not be read.") {
            val json = documentStore.readText(uri)
            val preview = previewCatalogImport(json)
            pendingImportJson = if (preview.valid) json else null
            _uiState.update {
                it.copy(
                    importPreview = preview,
                    confirmImport = false,
                    importMode = ImportMode.MERGE,
                    errorMessage = if (preview.valid) null else preview.invalidReason ?: "That file is not a Master Control catalog backup.",
                )
            }
        }
    }

    fun onImportModeChange(mode: ImportMode) = _uiState.update { it.copy(importMode = mode) }

    fun onImportReview() = _uiState.update { it.copy(confirmImport = true) }

    fun onDismissImport() = _uiState.update {
        pendingImportJson = null
        it.copy(importPreview = null, confirmImport = false)
    }

    fun onConfirmImport() {
        val json = pendingImportJson
        val mode = _uiState.value.importMode
        if (json == null) {
            _uiState.update { it.copy(confirmImport = false, errorMessage = "The backup is no longer loaded. Pick the file again.") }
            return
        }
        runGuarded("The catalog could not be imported.") {
            val result = importCatalog(json, mode)
            pendingImportJson = null
            refreshDiagnostics()
            _uiState.update {
                it.copy(
                    importPreview = null,
                    confirmImport = false,
                    infoMessage = "Imported ${result.items.size} catalog item(s), " +
                        "${result.categories.size} category(ies) and ${result.folders.size} folder(s).",
                )
            }
        }
    }

    // ---- diagnostics ------------------------------------------------------

    fun onRunReconciliation() {
        runGuarded("Reconciliation could not run.", label = "Checking Telegram mappings…") {
            val report = reconcile()
            _uiState.update {
                it.copy(
                    lastReconciliation = report,
                    infoMessage = "Checked ${report.mappingsChecked} mapping(s): ${report.mappingsOk} intact, " +
                        "${report.mappingsStale} stale, ${report.mappingsRemoteMissing} missing on Telegram, " +
                        "${report.unmanagedFound} unmanaged item(s) found.",
                )
            }
            refreshDiagnostics()
        }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun applySetting(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(errorMessage = (t as? AppError)?.userMessage ?: failureMessage)
                }
            }
        }
    }

    private fun runGuarded(failureMessage: String, label: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, busyLabel = label) }
            try {
                block()
                _uiState.update { it.copy(busy = false, busyLabel = null) }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(busy = false, busyLabel = null) }
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        busyLabel = null,
                        pinEditor = it.pinEditor?.copy(saving = false),
                        errorMessage = (t as? AppError)?.userMessage ?: failureMessage,
                    )
                }
            }
        }
    }

    private companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_CONCURRENT_UPLOADS = 4
    }
}

internal fun AppLockMode.label(): String = when (this) {
    AppLockMode.PIN -> "Master Control PIN"
    AppLockMode.BIOMETRIC -> "Biometric (fingerprint or face)"
    AppLockMode.DEVICE_CREDENTIAL -> "Device screen lock"
}
