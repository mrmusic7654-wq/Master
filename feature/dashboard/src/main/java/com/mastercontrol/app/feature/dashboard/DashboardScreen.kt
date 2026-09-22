package com.mastercontrol.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.common.format.DateTimeFormat
import com.mastercontrol.app.core.common.format.Formatters
import com.mastercontrol.app.core.ui.adaptive.McWindowClass
import com.mastercontrol.app.core.ui.adaptive.rememberWindowClass
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.ErrorState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McAction
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.SectionCard
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatTile
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.component.TransferProgressBar
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.LibraryStatistics
import com.mastercontrol.app.domain.model.UploadTask
import androidx.compose.foundation.layout.BoxWithConstraints

/**
 * Dashboard.
 *
 * Navigation targets are passed in as callbacks so this module owns no knowledge
 * of the NavHost (the app module wires them).
 */
@Composable
fun DashboardRoute(
    onOpenLibrary: () -> Unit,
    onAddVideo: () -> Unit,
    onOpenUploads: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenActivity: () -> Unit,
    onConfigureTelegram: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        modifier = modifier,
        onRefresh = viewModel::refresh,
        onDismissError = viewModel::dismissError,
        onOpenLibrary = onOpenLibrary,
        onAddVideo = onAddVideo,
        onOpenUploads = onOpenUploads,
        onOpenChannels = onOpenChannels,
        onOpenCategories = onOpenCategories,
        onOpenFolders = onOpenFolders,
        onOpenActivity = onOpenActivity,
        onConfigureTelegram = onConfigureTelegram,
    )
}

@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onAddVideo: () -> Unit = {},
    onOpenUploads: () -> Unit = {},
    onOpenChannels: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenFolders: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onConfigureTelegram: () -> Unit = {},
) {
    if (state.loading) {
        LoadingState(modifier = modifier, message = "Reading the local catalog…")
        return
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val windowClass = rememberWindowClass()
        val tilesPerRow = if (windowClass == McWindowClass.COMPACT) 2 else 3

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = McDimens.SpacingLg)
                .padding(bottom = McDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(McDimens.SpacingLg),
        ) {
            if (state.errorMessage != null) {
                ErrorState(
                    message = state.errorMessage ?: "",
                    actions = listOf(
                        McAction("Retry", onRefresh, emphasized = true),
                        McAction("Dismiss", onDismissError),
                    ),
                )
            }

            AccountStatusCard(
                state = state,
                onConfigureTelegram = onConfigureTelegram,
                onOpenChannels = onOpenChannels,
                onRefresh = onRefresh,
            )

            StorageSection(statistics = state.statistics, tilesPerRow = tilesPerRow, onOpenLibrary = onOpenLibrary)

            UploadSection(
                active = state.activeUploads,
                queued = state.queuedUploads,
                failed = state.failedUploads,
                pendingBytes = state.statistics.pendingUploadBytes,
                onOpenUploads = onOpenUploads,
            )

            QuickActions(
                onAddVideo = onAddVideo,
                onOpenUploads = onOpenUploads,
                onOpenChannels = onOpenChannels,
                onOpenCategories = onOpenCategories,
                onOpenFolders = onOpenFolders,
            )

            RecentActivitySection(entries = state.statistics.recentActivity, onOpenActivity = onOpenActivity)

            SectionHint(
                "Local device storage and Telegram-referenced storage are reported separately. " +
                    "Master Control cannot read Telegram account quotas, so none are shown.",
            )
        }
    }
}

@Composable
private fun AccountStatusCard(
    state: DashboardUiState,
    onConfigureTelegram: () -> Unit,
    onOpenChannels: () -> Unit,
    onRefresh: () -> Unit,
) {
    val greeting = state.account?.displayName
        ?: (state.authorizationState as? com.mastercontrol.app.domain.model.AuthorizationState.Ready)?.displayName

    SectionCard(
        title = greeting?.let { "Signed in as $it" } ?: "Telegram not authorized",
        subtitle = connectionText(state.connectionState),
        leadingIcon = if (state.isAuthorized) Icons.Filled.CloudDone else Icons.Filled.Warning,
        trailing = {
            TextButton(onClick = onRefresh) {
                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Refresh")
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            StatusChip(
                text = if (state.isAuthorized) "Authorized" else "Authorization required",
                tone = if (state.isAuthorized) McTone.SUCCESS else McTone.WARNING,
                icon = if (state.isAuthorized) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            )
            StatusChip(
                text = connectionChipText(state.connectionState),
                tone = connectionTone(state.connectionState),
            )
            if (state.defaultChannel != null) {
                StatusChip(
                    text = if (state.channelReady) "Channel ready" else "Channel missing rights",
                    tone = if (state.channelReady) McTone.SUCCESS else McTone.DANGER,
                )
            }
        }
        Spacer(Modifier.height(McDimens.SpacingMd))
        DataRow(label = "Storage channel", value = state.defaultChannel?.displayName)
        DataRow(label = "Channel ID", value = state.defaultChannel?.id?.toString(), monospaced = true)
        DataRow(label = "Channels configured", value = state.channelCount.toString())
        if (state.tdlibVersion.isNotBlank()) {
            DataRow(label = "TDLib", value = state.tdlibVersion, monospaced = true)
        }

        when {
            !state.isAuthorized -> {
                Spacer(Modifier.height(McDimens.SpacingMd))
                NoticeBar(
                    message = "Telegram is not authorized on this device. Uploads stay queued until sign-in completes.",
                    tone = McTone.WARNING,
                    icon = Icons.Filled.Warning,
                    action = McAction("Set up Telegram", onConfigureTelegram),
                )
            }
            state.defaultChannel == null -> {
                Spacer(Modifier.height(McDimens.SpacingMd))
                NoticeBar(
                    message = "No storage channel selected. Choose the channel Master Control uploads to.",
                    tone = McTone.INFO,
                    icon = Icons.Filled.CloudUpload,
                    action = McAction("Choose channel", onOpenChannels),
                )
            }
            !state.channelReady -> {
                Spacer(Modifier.height(McDimens.SpacingMd))
                NoticeBar(
                    message = "The selected channel does not grant posting rights. Missing: " +
                        (state.defaultChannel?.permissions?.missingForUpload?.joinToString(", ") ?: "unknown"),
                    tone = McTone.DANGER,
                    icon = Icons.Filled.Error,
                    action = McAction("Manage channels", onOpenChannels),
                )
            }
        }
    }
}

/** One metric tile, built from real catalog values. */
private data class StatTileSpec(
    val label: String,
    val value: String,
    val caption: String? = null,
    val icon: ImageVector? = null,
    val tone: McTone = McTone.NEUTRAL,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun StorageSection(
    statistics: LibraryStatistics,
    tilesPerRow: Int,
    onOpenLibrary: () -> Unit,
) {
    val tiles = listOf(
        StatTileSpec(
            label = "Total videos",
            value = statistics.totalVideos.toString(),
            caption = "${statistics.completedMediaCount} uploaded to Telegram",
            icon = Icons.Filled.VideoLibrary,
            tone = McTone.INFO,
            onClick = onOpenLibrary,
        ),
        StatTileSpec(
            label = "Local media",
            value = Formatters.bytes(statistics.totalLocalMediaBytes),
            caption = "Source files referenced on this device",
            icon = Icons.Filled.Storage,
        ),
        StatTileSpec(
            label = "Telegram storage",
            value = Formatters.bytes(statistics.totalTelegramReferencedBytes),
            caption = "Sum of active Telegram mappings",
            icon = Icons.Filled.CloudDone,
            tone = McTone.SUCCESS,
        ),
        StatTileSpec(
            label = "Local thumbnails",
            value = statistics.totalLocalThumbnails.toString(),
            caption = "Generated posters on this device",
            icon = Icons.Filled.Image,
        ),
        StatTileSpec(
            label = "Pending upload",
            value = Formatters.bytes(statistics.pendingUploadBytes),
            caption = "${statistics.pendingUploads} task(s) queued",
            icon = Icons.Filled.Schedule,
            tone = if (statistics.pendingUploads > 0) McTone.WARNING else McTone.NEUTRAL,
        ),
        StatTileSpec(
            label = "Failed uploads",
            value = statistics.failedUploads.toString(),
            caption = if (statistics.remoteDeletedCount > 0) {
                "${statistics.remoteDeletedCount} mapping(s) reference deleted media"
            } else {
                "Uploads needing attention"
            },
            icon = Icons.Filled.Error,
            tone = if (statistics.failedUploads > 0) McTone.DANGER else McTone.SUCCESS,
        ),
        StatTileSpec(
            label = "Categories",
            value = statistics.totalCategories.toString(),
            icon = Icons.Filled.Category,
        ),
        StatTileSpec(
            label = "Folders",
            value = statistics.totalFolders.toString(),
            icon = Icons.Filled.Folder,
        ),
        StatTileSpec(
            label = "Tags",
            value = statistics.totalTags.toString(),
            icon = Icons.Filled.PhotoLibrary,
        ),
    )

    SectionCard(
        title = "Library and storage",
        subtitle = "${statistics.totalVideos} catalogued video(s)",
        leadingIcon = Icons.Filled.VideoLibrary,
        trailing = { TextButton(onClick = onOpenLibrary) { Text("Open library") } },
    ) {
        TileGrid(tiles = tiles, tilesPerRow = tilesPerRow)
        Spacer(Modifier.height(McDimens.SpacingMd))
        SectionHint(
            "Local device storage and Telegram-referenced storage are measured separately. " +
                "Telegram does not expose an account quota, so none is claimed here.",
        )
    }
}

/**
 * Renders tiles in fixed rows of [tilesPerRow] with equal widths.
 *
 * Chunked rows (rather than a lazy grid or a weighted FlowRow) keep measurement
 * cheap and the layout identical on phones and tablets.
 */
@Composable
private fun TileGrid(tiles: List<StatTileSpec>, tilesPerRow: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd)) {
        tiles.chunked(tilesPerRow).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingMd)) {
                rowTiles.forEach { tile ->
                    StatTile(
                        label = tile.label,
                        value = tile.value,
                        caption = tile.caption,
                        icon = tile.icon,
                        tone = tile.tone,
                        onClick = tile.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep the last row aligned with the rows above it.
                repeat(tilesPerRow - rowTiles.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun UploadSection(
    active: List<UploadTask>,
    queued: List<UploadTask>,
    failed: List<UploadTask>,
    pendingBytes: Long,
    onOpenUploads: () -> Unit,
) {
    SectionCard(
        title = "Upload queue",
        subtitle = buildString {
            append(active.size).append(" transferring · ")
            append(queued.size).append(" waiting")
            if (failed.isNotEmpty()) append(" · ").append(failed.size).append(" failed")
        },
        leadingIcon = Icons.Filled.Upload,
        trailing = { TextButton(onClick = onOpenUploads) { Text("Manage") } },
    ) {
        if (active.isEmpty() && queued.isEmpty() && failed.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CloudUpload,
                title = "Your upload queue is empty",
                message = "Import a video and queue it. Transfers run in the background and " +
                    "resume after interruptions.",
            )
            return@SectionCard
        }

        active.forEach { task ->
            TransferProgressBar(
                progress = if ((task.totalBytes ?: 0L) > 0L) task.progress else null,
                label = task.videoId,
                detail = if ((task.totalBytes ?: 0L) > 0L) {
                    "${Formatters.bytes(task.bytesUploaded)} of ${Formatters.bytes(task.totalBytes)}"
                } else {
                    "${Formatters.bytes(task.bytesUploaded)} transferred"
                },
                tone = McTone.INFO,
                modifier = Modifier.padding(vertical = McDimens.SpacingXs),
            )
        }
        if (queued.isNotEmpty()) {
            Spacer(Modifier.height(McDimens.SpacingSm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(McDimens.SpacingSm))
            DataRow(
                label = "Waiting",
                value = queued.joinToString(", ") { it.videoId },
                monospaced = true,
            )
            DataRow(label = "Queued bytes", value = Formatters.bytes(pendingBytes))
        }
        if (failed.isNotEmpty()) {
            Spacer(Modifier.height(McDimens.SpacingSm))
            NoticeBar(
                message = "${failed.size} upload(s) failed and need attention.",
                tone = McTone.DANGER,
                icon = Icons.Filled.Error,
                action = McAction("Review", onOpenUploads),
            )
        }
    }
}

@Composable
private fun QuickActions(
    onAddVideo: () -> Unit,
    onOpenUploads: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenFolders: () -> Unit,
) {
    SectionCard(title = "Quick actions", leadingIcon = Icons.Filled.CloudUpload) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
        ) {
            Button(onClick = onAddVideo, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Add video")
            }
            OutlinedButton(onClick = onOpenUploads, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Uploads")
            }
        }
        Spacer(Modifier.height(McDimens.SpacingSm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
        ) {
            OutlinedButton(onClick = onOpenChannels, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Channels")
            }
            OutlinedButton(onClick = onOpenCategories, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Category")
            }
            OutlinedButton(onClick = onOpenFolders, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Folder")
            }
        }
    }
}

@Composable
private fun RecentActivitySection(entries: List<ActivityLogEntry>, onOpenActivity: () -> Unit) {
    SectionCard(
        title = "Recent activity",
        subtitle = if (entries.isEmpty()) "Nothing recorded yet" else "${entries.size} latest event(s)",
        leadingIcon = Icons.Filled.History,
        trailing = { TextButton(onClick = onOpenActivity) { Text("View all") } },
    ) {
        if (entries.isEmpty()) {
            SectionHint("Imports, uploads, metadata edits and channel changes are recorded here.")
            return@SectionCard
        }
        entries.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = McDimens.SpacingXs)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    entry.details?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(McDimens.SpacingSm))
                Text(
                    text = DateTimeFormat.relative(entry.createdAt.toEpochMilli()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index != entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun connectionText(state: ConnectionState): String = when (state) {
    ConnectionState.READY -> "Connected to Telegram"
    ConnectionState.CONNECTING -> "Connecting to Telegram"
    ConnectionState.UPDATING -> "Synchronizing with Telegram"
    ConnectionState.WAITING_FOR_NETWORK -> "Waiting for a network connection"
    ConnectionState.DISCONNECTED -> "Disconnected from Telegram"
    ConnectionState.UNKNOWN -> "Telegram engine not started"
}

private fun connectionChipText(state: ConnectionState): String = when (state) {
    ConnectionState.READY -> "Connected"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.UPDATING -> "Updating"
    ConnectionState.WAITING_FOR_NETWORK -> "Waiting for network"
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.UNKNOWN -> "Engine idle"
}

private fun connectionTone(state: ConnectionState): McTone = when (state) {
    ConnectionState.READY -> McTone.SUCCESS
    ConnectionState.CONNECTING, ConnectionState.UPDATING -> McTone.INFO
    ConnectionState.WAITING_FOR_NETWORK, ConnectionState.DISCONNECTED -> McTone.WARNING
    ConnectionState.UNKNOWN -> McTone.NEUTRAL
}
