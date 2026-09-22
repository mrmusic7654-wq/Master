package com.mastercontrol.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.common.text.SecretMasking
import com.mastercontrol.app.core.ui.component.CopyValueRow
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.McChoice
import com.mastercontrol.app.core.ui.component.McChoiceDialog
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.SectionCard
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.domain.repository.AppLockMode

private const val EXPORT_MIME = "application/json"
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")

/**
 * Settings screen.
 *
 * Sections are ordered by consequence: Telegram account, security, uploads,
 * library defaults, appearance, backup and diagnostics. Values that are secrets
 * are shown masked only.
 */
@Composable
fun SettingsRoute(
    onEditCredentials: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onOpenChannels: () -> Unit,
    onRequireOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_MIME),
    ) { uri -> if (uri != null) viewModel.onExportDestinationPicked(uri.toString()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onImportFilePicked(uri.toString()) }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissMessage()
        }
    }
    LaunchedEffect(state.loggedOut, state.secretsCleared) {
        if (state.loggedOut || state.secretsCleared) onRequireOnboarding()
    }

    SettingsScreen(
        state = state,
        modifier = modifier,
        onEditCredentials = onEditCredentials,
        onOpenActivityLog = onOpenActivityLog,
        onOpenChannels = onOpenChannels,
        onExportClick = { exportLauncher.launch(state.exportFileName.ifBlank { "master-control-catalog.json" }) },
        onImportClick = { importLauncher.launch(IMPORT_MIME_TYPES) },
        onThemeModeChange = viewModel::onThemeModeChange,
        onDynamicColorChange = viewModel::onDynamicColorChange,
        onLibraryLayoutChange = viewModel::onLibraryLayoutChange,
        onLibrarySortChange = viewModel::onLibrarySortChange,
        onUploadNetworkRuleChange = viewModel::onUploadNetworkRuleChange,
        onChargingOnlyChange = viewModel::onChargingOnlyChange,
        onMaxConcurrentUploadsChange = viewModel::onMaxConcurrentUploadsChange,
        onMediaHashingChange = viewModel::onMediaHashingChange,
        onThumbnailQualityChange = viewModel::onThumbnailQualityChange,
        onThumbnailCaptureChange = viewModel::onThumbnailCaptureChange,
        onCaptionVideoIdChange = viewModel::onCaptionVideoIdChange,
        onAutoDeleteLocalCopyChange = viewModel::onAutoDeleteLocalCopyChange,
        onSetPinClick = viewModel::onSetPinClick,
        onPinEditorChange = viewModel::onPinEditorChange,
        onDismissPinEditor = viewModel::onDismissPinEditor,
        onSavePin = viewModel::onSavePin,
        onLockModePickerOpen = viewModel::onLockModePickerOpen,
        onLockModePickerDismiss = viewModel::onLockModePickerDismiss,
        onLockModeSelected = viewModel::onLockModeSelected,
        onDisableLockClick = viewModel::onDisableLockClick,
        onDismissDisableLock = viewModel::onDismissDisableLock,
        onConfirmDisableLock = viewModel::onConfirmDisableLock,
        onLogoutClick = viewModel::onLogoutClick,
        onDismissLogout = viewModel::onDismissLogout,
        onConfirmLogout = viewModel::onConfirmLogout,
        onRestartEngineClick = viewModel::onRestartEngineClick,
        onDismissRestartEngine = viewModel::onDismissRestartEngine,
        onConfirmRestartEngine = viewModel::onConfirmRestartEngine,
        onClearSecretsClick = viewModel::onClearSecretsClick,
        onDismissClearSecrets = viewModel::onDismissClearSecrets,
        onConfirmClearSecrets = viewModel::onConfirmClearSecrets,
        onImportModeChange = viewModel::onImportModeChange,
        onImportReview = viewModel::onImportReview,
        onDismissImport = viewModel::onDismissImport,
        onConfirmImport = viewModel::onConfirmImport,
        onRunReconciliation = viewModel::onRunReconciliation,
        onRefreshDiagnostics = viewModel::onRefreshDiagnostics,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onEditCredentials: () -> Unit = {},
    onOpenActivityLog: () -> Unit = {},
    onOpenChannels: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onThemeModeChange: (com.mastercontrol.app.domain.model.ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onLibraryLayoutChange: (com.mastercontrol.app.domain.model.LibraryLayout) -> Unit = {},
    onLibrarySortChange: (com.mastercontrol.app.domain.model.LibrarySort) -> Unit = {},
    onUploadNetworkRuleChange: (com.mastercontrol.app.domain.model.UploadNetworkRule) -> Unit = {},
    onChargingOnlyChange: (Boolean) -> Unit = {},
    onMaxConcurrentUploadsChange: (Int) -> Unit = {},
    onMediaHashingChange: (Boolean) -> Unit = {},
    onThumbnailQualityChange: (Boolean) -> Unit = {},
    onThumbnailCaptureChange: (Long) -> Unit = {},
    onCaptionVideoIdChange: (Boolean) -> Unit = {},
    onAutoDeleteLocalCopyChange: (Boolean) -> Unit = {},
    onSetPinClick: () -> Unit = {},
    onPinEditorChange: ((PinEditor) -> PinEditor) -> Unit = {},
    onDismissPinEditor: () -> Unit = {},
    onSavePin: () -> Unit = {},
    onLockModePickerOpen: () -> Unit = {},
    onLockModePickerDismiss: () -> Unit = {},
    onLockModeSelected: (AppLockMode) -> Unit = {},
    onDisableLockClick: () -> Unit = {},
    onDismissDisableLock: () -> Unit = {},
    onConfirmDisableLock: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onDismissLogout: () -> Unit = {},
    onConfirmLogout: () -> Unit = {},
    onRestartEngineClick: () -> Unit = {},
    onDismissRestartEngine: () -> Unit = {},
    onConfirmRestartEngine: () -> Unit = {},
    onClearSecretsClick: () -> Unit = {},
    onDismissClearSecrets: () -> Unit = {},
    onConfirmClearSecrets: () -> Unit = {},
    onImportModeChange: (com.mastercontrol.app.domain.model.ImportMode) -> Unit = {},
    onImportReview: () -> Unit = {},
    onDismissImport: () -> Unit = {},
    onConfirmImport: () -> Unit = {},
    onRunReconciliation: () -> Unit = {},
    onRefreshDiagnostics: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(McDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
        ) {
            item(key = "account") {
                AccountSection(
                    state = state,
                    onEditCredentials = onEditCredentials,
                    onRestartEngineClick = onRestartEngineClick,
                    onLogoutClick = onLogoutClick,
                    onOpenChannels = onOpenChannels,
                )
            }
            item(key = "security") {
                SecuritySection(
                    state = state,
                    onSetPinClick = onSetPinClick,
                    onLockModePickerOpen = onLockModePickerOpen,
                    onDisableLockClick = onDisableLockClick,
                    onClearSecretsClick = onClearSecretsClick,
                )
            }
            item(key = "uploads") {
                UploadSettingsSection(
                    state = state,
                    onUploadNetworkRuleChange = onUploadNetworkRuleChange,
                    onChargingOnlyChange = onChargingOnlyChange,
                    onMaxConcurrentUploadsChange = onMaxConcurrentUploadsChange,
                    onMediaHashingChange = onMediaHashingChange,
                    onThumbnailQualityChange = onThumbnailQualityChange,
                    onThumbnailCaptureChange = onThumbnailCaptureChange,
                    onCaptionVideoIdChange = onCaptionVideoIdChange,
                    onAutoDeleteLocalCopyChange = onAutoDeleteLocalCopyChange,
                )
            }
            item(key = "library") {
                LibrarySettingsSection(
                    state = state,
                    onLibraryLayoutChange = onLibraryLayoutChange,
                    onLibrarySortChange = onLibrarySortChange,
                )
            }
            item(key = "appearance") {
                AppearanceSection(
                    state = state,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                )
            }
            item(key = "backup") {
                BackupSection(
                    state = state,
                    onExportClick = onExportClick,
                    onImportClick = onImportClick,
                )
            }
            item(key = "diagnostics") {
                DiagnosticsSection(
                    state = state,
                    onRunReconciliation = onRunReconciliation,
                    onRefreshDiagnostics = onRefreshDiagnostics,
                    onOpenActivityLog = onOpenActivityLog,
                )
            }
        }

        if (state.busy && state.busyLabel != null) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(McDimens.SpacingLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(McDimens.SpacingSm))
                Text(state.busyLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    state.pinEditor?.let { editor ->
        PinDialog(
            editor = editor,
            onChange = onPinEditorChange,
            onSave = onSavePin,
            onDismiss = onDismissPinEditor,
        )
    }

    if (state.lockModePickerOpen) {
        McChoiceDialog(
            title = "Unlock method",
            supportingText = "Biometric and device-credential unlock are handled by Android; " +
                "Master Control stores no secret for them.",
            selectedKey = state.lockMode.name,
            options = AppLockMode.entries.map { mode ->
                McChoice(
                    key = mode.name,
                    label = mode.label(),
                    description = when (mode) {
                        AppLockMode.PIN -> "A PIN hashed inside the Android Keystore"
                        AppLockMode.BIOMETRIC -> "Fingerprint or face unlock"
                        AppLockMode.DEVICE_CREDENTIAL -> "Whatever unlocks this device"
                    },
                )
            },
            onSelect = { choice ->
                AppLockMode.entries.firstOrNull { it.name == choice.key }?.let(onLockModeSelected)
            },
            onDismiss = onLockModePickerDismiss,
        )
    }

    if (state.confirmDisableLock) {
        McConfirmDialog(
            title = "Turn off the app lock?",
            message = "Master Control will open without asking for anything. Your stored PIN hash " +
                "is deleted from the Keystore-backed secret store. The catalog and Telegram session are kept.",
            confirmLabel = "Turn off",
            dismissLabel = "Keep lock",
            destructive = true,
            acknowledgementLabel = "I understand anyone with this device can open Master Control",
            icon = Icons.Filled.Warning,
            onConfirm = onConfirmDisableLock,
            onDismiss = onDismissDisableLock,
        )
    }

    if (state.confirmLogout) {
        McConfirmDialog(
            title = "Sign out of Telegram?",
            message = "The TDLib session on this device is closed. Your catalog, permanent video IDs " +
                "and recorded mappings stay on this device, but uploads stop until you sign in again. " +
                "Media already in Telegram is untouched.",
            confirmLabel = "Sign out",
            dismissLabel = "Stay signed in",
            destructive = true,
            acknowledgementLabel = "I understand uploads stop until I sign in again",
            icon = Icons.Filled.Logout,
            onConfirm = onConfirmLogout,
            onDismiss = onDismissLogout,
        )
    }

    if (state.confirmRestartEngine) {
        McConfirmDialog(
            title = "Restart the Telegram engine?",
            message = "TDLib is stopped and started again with the stored credentials. Queued uploads " +
                "resume on their own; an in-flight file restarts from the beginning.",
            confirmLabel = "Restart engine",
            dismissLabel = "Cancel",
            icon = Icons.Filled.RestartAlt,
            onConfirm = onConfirmRestartEngine,
            onDismiss = onDismissRestartEngine,
        )
    }

    if (state.confirmClearSecrets) {
        McConfirmDialog(
            title = "Clear every stored secret?",
            message = "The Telegram API ID and API hash, the TDLib encryption key material and the " +
                "app lock PIN hash are deleted from the Keystore-backed store. The catalog stays, but " +
                "you must enter your API credentials and sign in again.",
            confirmLabel = "Clear secrets",
            dismissLabel = "Keep",
            destructive = true,
            acknowledgementLabel = "I understand I will need my api_id and api_hash again",
            icon = Icons.Filled.Delete,
            onConfirm = onConfirmClearSecrets,
            onDismiss = onDismissClearSecrets,
        )
    }

    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            state = state,
            onModeChange = onImportModeChange,
            onReview = onImportReview,
            onDismiss = onDismissImport,
        )
    }

    if (state.confirmImport && state.importPreview != null) {
        McConfirmDialog(
            title = "Import this catalog backup?",
            message = buildString {
                val preview = state.importPreview
                append("${preview?.items?.size ?: 0} catalog item(s) are in this file. ")
                if (state.importMode == com.mastercontrol.app.domain.model.ImportMode.REPLACE_CONFLICTS) {
                    append("Conflicting entries are overwritten with the backup's values. ")
                } else {
                    append("Existing entries are kept; only missing ones are added. ")
                }
                append("Nothing is deleted and no Telegram media is touched.")
            },
            confirmLabel = "Import",
            dismissLabel = "Cancel",
            destructive = state.importMode == com.mastercontrol.app.domain.model.ImportMode.REPLACE_CONFLICTS,
            acknowledgementLabel = if (state.importMode == com.mastercontrol.app.domain.model.ImportMode.REPLACE_CONFLICTS) {
                "I understand conflicting catalog entries are overwritten"
            } else {
                null
            },
            icon = Icons.Filled.Backup,
            onConfirm = onConfirmImport,
            onDismiss = onDismissImport,
        )
    }
}

@Composable
private fun AccountSection(
    state: SettingsUiState,
    onEditCredentials: () -> Unit,
    onRestartEngineClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Telegram account",
        subtitle = state.accountInfo?.displayName ?: "Not signed in",
        leadingIcon = Icons.Filled.Key,
        modifier = modifier,
    ) {
        StatusChip(
            text = state.authorizationState.label(),
            tone = if (state.isAuthenticated) McTone.SUCCESS else McTone.WARNING,
            icon = if (state.isAuthenticated) Icons.Filled.Security else Icons.Filled.Warning,
        )
        Spacer(Modifier.height(McDimens.SpacingXs))
        StatusChip(text = state.connectionState.label(), tone = state.connectionState.tone())

        Spacer(Modifier.height(McDimens.SpacingMd))
        DataRow(label = "Account", value = state.accountInfo?.displayName)
        DataRow(label = "Username", value = state.accountInfo?.username?.let { "@$it" })
        DataRow(
            label = "Phone number",
            value = state.accountInfo?.phoneNumber?.let { SecretMasking.maskPhoneNumber(it) },
        )
        DataRow(label = "TDLib version", value = state.tdlibVersion)
        CopyValueRow(label = "API ID", value = state.maskedApiId)
        CopyValueRow(label = "API hash", value = state.maskedApiHash)
        if (!state.credentialsConfigured) {
            Spacer(Modifier.height(McDimens.SpacingSm))
            NoticeBar(
                message = "No Telegram API credentials are stored. Enter your api_id and api_hash to start the engine.",
                tone = McTone.WARNING,
                icon = Icons.Filled.Warning,
            )
        }
        SectionHint(
            "API credentials are stored encrypted in the Android Keystore and are shown masked. " +
                "They are never logged, exported or sent anywhere except to Telegram.",
        )

        Spacer(Modifier.height(McDimens.SpacingMd))
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(onClick = onEditCredentials, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Credentials")
            }
            OutlinedButton(onClick = onOpenChannels, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Channels")
            }
        }
        Spacer(Modifier.height(McDimens.SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(onClick = onRestartEngineClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Restart engine")
            }
            OutlinedButton(
                onClick = onLogoutClick,
                enabled = !state.busy && state.isAuthenticated,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun SecuritySection(
    state: SettingsUiState,
    onSetPinClick: () -> Unit,
    onLockModePickerOpen: () -> Unit,
    onDisableLockClick: () -> Unit,
    onClearSecretsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "App lock and secrets",
        subtitle = if (state.lockEnabled) "Locked with ${state.lockMode.label().lowercase()}" else "No app lock",
        leadingIcon = Icons.Filled.Lock,
        modifier = modifier,
    ) {
        StatusChip(
            text = if (state.lockEnabled) "App lock on" else "App lock off",
            tone = if (state.lockEnabled) McTone.SUCCESS else McTone.WARNING,
            icon = if (state.lockEnabled) Icons.Filled.Lock else Icons.Filled.Warning,
        )
        Spacer(Modifier.height(McDimens.SpacingSm))
        SectionHint(
            "The lock is checked before the catalog is shown. A PIN is stored only as a hash " +
                "protected by the Android Keystore; biometric and device-credential unlock are " +
                "performed by Android itself.",
        )
        Spacer(Modifier.height(McDimens.SpacingMd))
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(onClick = onSetPinClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text(if (state.lockEnabled && state.lockMode == AppLockMode.PIN) "Change PIN" else "Set PIN")
            }
            OutlinedButton(onClick = onLockModePickerOpen, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Unlock method")
            }
        }
        Spacer(Modifier.height(McDimens.SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(
                onClick = onDisableLockClick,
                enabled = !state.busy && state.lockEnabled,
                modifier = Modifier.weight(1f),
            ) { Text("Turn off lock") }
            OutlinedButton(onClick = onClearSecretsClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Clear secrets")
            }
        }
    }
}
