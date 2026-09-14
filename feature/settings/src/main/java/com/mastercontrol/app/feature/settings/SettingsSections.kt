package com.mastercontrol.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.McChipRow
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McSecretTextField
import com.mastercontrol.app.core.ui.component.McSelectableChip
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.SectionCard
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.ImportConflictKind
import com.mastercontrol.app.domain.model.ImportMode
import com.mastercontrol.app.domain.model.LibraryLayout
import com.mastercontrol.app.domain.model.LibrarySort
import com.mastercontrol.app.domain.model.ThemeMode
import com.mastercontrol.app.domain.model.ThumbnailQuality
import com.mastercontrol.app.domain.model.UploadNetworkRule

@Composable
internal fun UploadSettingsSection(
    state: SettingsUiState,
    onUploadNetworkRuleChange: (UploadNetworkRule) -> Unit,
    onChargingOnlyChange: (Boolean) -> Unit,
    onMaxConcurrentUploadsChange: (Int) -> Unit,
    onMediaHashingChange: (Boolean) -> Unit,
    onThumbnailQualityChange: (Boolean) -> Unit,
    onThumbnailCaptureChange: (Long) -> Unit,
    onCaptionVideoIdChange: (Boolean) -> Unit,
    onAutoDeleteLocalCopyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    SectionCard(
        title = "Uploads and import",
        subtitle = "Applies to the durable upload queue",
        leadingIcon = Icons.Filled.CloudUpload,
        modifier = modifier,
    ) {
        Text("Network", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        McChipRow {
            UploadNetworkRule.entries.forEach { rule ->
                McSelectableChip(
                    selected = settings.uploadNetworkRule == rule,
                    label = rule.label(),
                    onClick = { onUploadNetworkRuleChange(rule) },
                )
            }
        }
        SettingsSwitchRow(
            label = "Only while charging",
            description = "Uploads wait for a charger, which keeps long transfers off a draining battery.",
            checked = settings.allowUploadsOnlyWhileCharging,
            onCheckedChange = onChargingOnlyChange,
            enabled = !state.busy,
        )

        Spacer(Modifier.height(McDimens.SpacingSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Concurrent uploads", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Telegram rate limits apply per account; more parallel files rarely helps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { onMaxConcurrentUploadsChange(settings.maxConcurrentUploads - 1) },
                enabled = !state.busy && settings.maxConcurrentUploads > 1,
            ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer concurrent uploads") }
            Spacer(Modifier.width(McDimens.SpacingMd))
            Text(
                text = settings.maxConcurrentUploads.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(McDimens.SpacingMd))
            OutlinedButton(
                onClick = { onMaxConcurrentUploadsChange(settings.maxConcurrentUploads + 1) },
                enabled = !state.busy && settings.maxConcurrentUploads < 4,
            ) { Icon(Icons.Filled.Add, contentDescription = "More concurrent uploads") }
        }

        Spacer(Modifier.height(McDimens.SpacingMd))
        SettingsSwitchRow(
            label = "Compute checksums",
            description = "SHA-256 of each imported file, used to detect duplicates by content.",
            checked = settings.mediaHashingEnabled,
            onCheckedChange = onMediaHashingChange,
            enabled = !state.busy,
        )
        SettingsSwitchRow(
            label = "Include the video ID in captions",
            description = "Uploaded messages carry the permanent ID, which makes reconciliation unambiguous.",
            checked = settings.captionIncludesVideoId,
            onCheckedChange = onCaptionVideoIdChange,
            enabled = !state.busy,
        )
        SettingsSwitchRow(
            label = "Delete the local copy after upload",
            description = "Frees device storage once Telegram holds the media. The catalog row and mapping stay.",
            checked = settings.autoDeleteLocalCopyAfterUpload,
            onCheckedChange = onAutoDeleteLocalCopyChange,
            enabled = !state.busy,
        )
        if (settings.autoDeleteLocalCopyAfterUpload) {
            NoticeBar(
                message = "Local source files are removed after a verified upload. Re-downloading from " +
                    "Telegram is not part of Master Control.",
                tone = McTone.WARNING,
                icon = Icons.Filled.Warning,
            )
        }

        Spacer(Modifier.height(McDimens.SpacingMd))
        Text("Poster frame", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        McChipRow {
            McSelectableChip(
                selected = settings.thumbnailQuality == ThumbnailQuality.STANDARD,
                label = "Standard quality",
                onClick = { onThumbnailQualityChange(true) },
            )
            McSelectableChip(
                selected = settings.thumbnailQuality == ThumbnailQuality.HIGH,
                label = "High quality",
                onClick = { onThumbnailQualityChange(false) },
            )
        }
        McChipRow {
            POSTER_OFFSETS.forEach { (ms, label) ->
                McSelectableChip(
                    selected = settings.thumbnailCaptureMs == ms,
                    label = label,
                    onClick = { onThumbnailCaptureChange(ms) },
                )
            }
        }
        SectionHint("The poster is taken from the local file at this offset; no frame is fetched from Telegram.")
    }
}

private val POSTER_OFFSETS = listOf(
    1_000L to "at 1 s",
    5_000L to "at 5 s",
    15_000L to "at 15 s",
    60_000L to "at 1 min",
)

@Composable
internal fun LibrarySettingsSection(
    state: SettingsUiState,
    onLibraryLayoutChange: (LibraryLayout) -> Unit,
    onLibrarySortChange: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Library defaults",
        subtitle = "How the catalog opens",
        leadingIcon = Icons.Filled.VideoLibrary,
        modifier = modifier,
    ) {
        Text("Layout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        McChipRow {
            LibraryLayout.entries.forEach { layout ->
                McSelectableChip(
                    selected = state.settings.libraryLayout == layout,
                    label = layout.label(),
                    onClick = { onLibraryLayoutChange(layout) },
                )
            }
        }
        Spacer(Modifier.height(McDimens.SpacingSm))
        Text("Sort order", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        McChipRow {
            LibrarySort.entries.forEach { sort ->
                McSelectableChip(
                    selected = state.settings.librarySort == sort,
                    label = sort.label(),
                    onClick = { onLibrarySortChange(sort) },
                )
            }
        }
    }
}

@Composable
internal fun AppearanceSection(
    state: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Appearance",
        subtitle = "Theme and contrast",
        leadingIcon = Icons.Filled.Palette,
        modifier = modifier,
    ) {
        McChipRow {
            ThemeMode.entries.forEach { mode ->
                McSelectableChip(
                    selected = state.settings.themeMode == mode,
                    label = mode.label(),
                    onClick = { onThemeModeChange(mode) },
                )
            }
        }
        SettingsSwitchRow(
            label = "Dynamic color",
            description = "Derives the palette from your wallpaper on Android 12 and newer.",
            checked = state.settings.dynamicColor,
            onCheckedChange = onDynamicColorChange,
            enabled = !state.busy,
        )
        SectionHint(
            "Status is always shown with a word and an icon, never by colour alone, so the interface " +
                "stays readable in every theme and for colour-blind operators.",
        )
    }
}

@Composable
internal fun BackupSection(
    state: SettingsUiState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Catalog backup",
        subtitle = "Export and import the catalog as JSON",
        leadingIcon = Icons.Filled.Backup,
        modifier = modifier,
    ) {
        SectionHint(
            "A backup holds permanent video IDs, metadata, categories, folders and Telegram mappings. " +
                "It never contains your API hash, PIN, session keys or media files.",
        )
        Spacer(Modifier.height(McDimens.SpacingSm))
        DataRow(label = "Videos in catalog", value = state.counts.videos.toString())
        DataRow(label = "Suggested file name", value = state.exportFileName)
        Spacer(Modifier.height(McDimens.SpacingMd))
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(onClick = onExportClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Export")
            }
            OutlinedButton(onClick = onImportClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Import")
            }
        }
        SectionHint("You choose the location with the system file picker; Master Control writes nowhere else.")
    }
}

@Composable
internal fun DiagnosticsSection(
    state: SettingsUiState,
    onRunReconciliation: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onOpenActivityLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Diagnostics",
        subtitle = "Real counters and the reconciliation engine",
        leadingIcon = Icons.Filled.Biotech,
        modifier = modifier,
    ) {
        DataRow(label = "Catalog videos", value = state.counts.videos.toString())
        DataRow(label = "Mappings needing attention", value = state.counts.problematicMappings.toString())
        DataRow(label = "Activity log entries", value = state.counts.activityEntries.toString())
        DataRow(label = "TDLib", value = state.tdlibVersion)
        DataRow(label = "Connection", value = state.connectionState.label())
        DataRow(label = "Authorization", value = state.authorizationState.label())

        state.lastReconciliation?.let { report ->
            Spacer(Modifier.height(McDimens.SpacingSm))
            NoticeBar(
                message = "Last run: ${report.mappingsChecked} checked, ${report.mappingsOk} intact, " +
                    "${report.mappingsStale} stale, ${report.mappingsRemoteMissing} missing, " +
                    "${report.unmanagedFound} unmanaged.",
                tone = if (report.mappingsStale == 0 && report.mappingsRemoteMissing == 0) McTone.SUCCESS else McTone.WARNING,
                icon = Icons.Filled.Sync,
            )
        }

        Spacer(Modifier.height(McDimens.SpacingMd))
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(onClick = onRunReconciliation, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Reconcile now")
            }
            OutlinedButton(onClick = onRefreshDiagnostics, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Refresh")
            }
        }
        Spacer(Modifier.height(McDimens.SpacingSm))
        OutlinedButton(onClick = onOpenActivityLog, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(McDimens.SpacingXs))
            Text("Open activity log")
        }
        SectionHint(
            "Reconciliation compares recorded mappings with what Telegram reports and flags media it " +
                "does not recognize. It never deletes anything by itself.",
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = McDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(McDimens.SpacingMd))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun PinDialog(
    editor: PinEditor,
    onChange: ((PinEditor) -> PinEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!editor.saving) onDismiss() },
        title = { Text("App lock PIN", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
            ) {
                SectionHint(
                    "The PIN is hashed inside the Android Keystore-backed secret store. Master Control " +
                        "never stores, logs or exports the PIN itself.",
                )
                if (editor.requiresCurrent) {
                    McSecretTextField(
                        value = editor.currentPin,
                        onValueChange = { value -> onChange { it.copy(currentPin = value.filter(Char::isDigit), error = null) } },
                        label = "Current PIN",
                        enabled = !editor.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                McSecretTextField(
                    value = editor.newPin,
                    onValueChange = { value -> onChange { it.copy(newPin = value.filter(Char::isDigit), error = null) } },
                    label = "New PIN",
                    supportingText = "At least 4 digits.",
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                McSecretTextField(
                    value = editor.confirmPin,
                    onValueChange = { value -> onChange { it.copy(confirmPin = value.filter(Char::isDigit), error = null) } },
                    label = "Repeat new PIN",
                    errorText = editor.error,
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !editor.saving && editor.newPin.length >= 4 && editor.newPin == editor.confirmPin,
            ) { Text("Save PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !editor.saving) { Text("Cancel") } },
    )
}

@Composable
internal fun ImportPreviewDialog(
    state: SettingsUiState,
    onModeChange: (ImportMode) -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = state.importPreview ?: return
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Backup contents", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
            ) {
                if (!preview.valid) {
                    NoticeBar(
                        message = preview.invalidReason ?: "This file is not a Master Control catalog backup.",
                        tone = McTone.DANGER,
                        icon = Icons.Filled.Warning,
                    )
                    return@Column
                }
                DataRow(label = "Catalog items", value = preview.items.size.toString())
                DataRow(label = "Categories", value = preview.categories.joinToString(", ").ifBlank { "None" })
                DataRow(label = "Folders", value = preview.folders.joinToString(", ").ifBlank { "None" })
                val conflicts = preview.items.count { it.conflict != ImportConflictKind.NONE }
                DataRow(label = "Conflicts with this device", value = conflicts.toString())
                preview.items.take(PREVIEW_ROWS).forEach { item ->
                    StatusChip(
                        text = "${item.streamerItem.videoId} · ${item.conflict.label()}",
                        tone = if (item.conflict == ImportConflictKind.NONE) McTone.SUCCESS else McTone.WARNING,
                    )
                }
                if (preview.items.size > PREVIEW_ROWS) {
                    SectionHint("and ${preview.items.size - PREVIEW_ROWS} more item(s)")
                }
                Spacer(Modifier.height(McDimens.SpacingSm))
                Text("If an entry already exists", style = MaterialTheme.typography.labelMedium)
                McChipRow {
                    ImportMode.entries.forEach { mode ->
                        McSelectableChip(
                            selected = state.importMode == mode,
                            label = mode.label(),
                            onClick = { onModeChange(mode) },
                        )
                    }
                }
                SectionHint(
                    if (state.importMode == ImportMode.REPLACE_CONFLICTS) {
                        "Conflicting catalog entries are overwritten with the values from the backup."
                    } else {
                        "Existing entries are kept untouched; only entries this device does not have are added."
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onReview, enabled = !state.busy && preview.valid) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") } },
    )
}

private const val PREVIEW_ROWS = 8

internal fun UploadNetworkRule.label(): String = when (this) {
    UploadNetworkRule.ANY_NETWORK -> "Any network"
    UploadNetworkRule.WIFI_ONLY -> "Wi-Fi only"
}

internal fun LibraryLayout.label(): String = when (this) {
    LibraryLayout.GRID -> "Grid"
    LibraryLayout.LIST -> "List"
    LibraryLayout.COMPACT -> "Compact list"
}

internal fun LibrarySort.label(): String = when (this) {
    LibrarySort.DATE_ADDED_DESC -> "Newest first"
    LibrarySort.DATE_ADDED_ASC -> "Oldest first"
    LibrarySort.TITLE_ASC -> "Title A–Z"
    LibrarySort.TITLE_DESC -> "Title Z–A"
    LibrarySort.DURATION_DESC -> "Longest first"
    LibrarySort.SIZE_DESC -> "Largest first"
    LibrarySort.YEAR_DESC -> "Year (newest)"
    LibrarySort.STATUS_ASC -> "Status"
}

internal fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

internal fun ImportMode.label(): String = when (this) {
    ImportMode.MERGE -> "Keep existing entries"
    ImportMode.REPLACE_CONFLICTS -> "Overwrite conflicts"
}

internal fun ImportConflictKind.label(): String = when (this) {
    ImportConflictKind.NONE -> "new"
    ImportConflictKind.VIDEO_ID_EXISTS -> "ID already in this catalog"
    ImportConflictKind.TITLE_MATCH -> "same title as an existing entry"
}

internal fun ConnectionState.label(): String = when (this) {
    ConnectionState.READY -> "Connected"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.UPDATING -> "Updating"
    ConnectionState.WAITING_FOR_NETWORK -> "Waiting for network"
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.UNKNOWN -> "Engine idle"
}

internal fun ConnectionState.tone(): McTone = when (this) {
    ConnectionState.READY -> McTone.SUCCESS
    ConnectionState.CONNECTING, ConnectionState.UPDATING -> McTone.INFO
    ConnectionState.WAITING_FOR_NETWORK, ConnectionState.DISCONNECTED -> McTone.WARNING
    ConnectionState.UNKNOWN -> McTone.NEUTRAL
}

internal fun AuthorizationState.label(): String = when (this) {
    is AuthorizationState.NeedsConfiguration -> "API credentials missing"
    is AuthorizationState.WaitPhoneNumber -> "Phone number required"
    is AuthorizationState.WaitCode -> "Verification code required"
    is AuthorizationState.WaitPassword -> "Two-factor password required"
    is AuthorizationState.WaitRegistration -> "Registration required"
    is AuthorizationState.WaitEmailAddress -> "Email address required"
    is AuthorizationState.WaitEmailCode -> "Email code required"
    is AuthorizationState.WaitOtherDeviceConfirmation -> "Confirmation on another device required"
    is AuthorizationState.WaitPremiumPurchase -> "Telegram Premium purchase required"
    is AuthorizationState.Ready -> "Signed in"
    is AuthorizationState.LoggingOut -> "Signing out"
    is AuthorizationState.Closing -> "Closing session"
    is AuthorizationState.Closed -> "Signed out"
    is AuthorizationState.Other -> "Unrecognized Telegram state"
}
