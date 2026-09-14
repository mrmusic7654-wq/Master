package com.mastercontrol.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mastercontrol.app.core.common.format.DateTimeFormat
import com.mastercontrol.app.core.common.format.Formatters
import com.mastercontrol.app.core.ui.adaptive.gridColumnsFor
import com.mastercontrol.app.core.ui.adaptive.rememberWindowClass
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McAction
import com.mastercontrol.app.core.ui.component.McChoice
import com.mastercontrol.app.core.ui.component.McChoiceDialog
import com.mastercontrol.app.core.ui.component.McChipRow
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McIconAction
import com.mastercontrol.app.core.ui.component.McSelectableChip
import com.mastercontrol.app.core.ui.component.McTextField
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.theme.MonoLabelStyle
import com.mastercontrol.app.domain.model.LibrarySort
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus

private const val VIDEO_MIME_PATTERN = "video/*"

/**
 * Library screen: search, faceted filters, sort, grid/list views and SAF import.
 *
 * Thumbnails are loaded from files Master Control generated itself; nothing is
 * fetched from Telegram here, and no video is ever played back inside this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var replaceConfirmVisible by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onVideoPicked(uri.toString()) }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissMessage()
        }
    }

    // rememberWindowClass() is a BoxWithConstraintsScope extension: the column
    // count is derived from the real available width, not from a guess.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val windowClass = rememberWindowClass()
        val columns = gridColumnsFor(
            windowClass = windowClass,
            minCellWidth = McDimens.GridMinCellWidth,
            availableWidth = maxWidth,
        )
        LaunchedEffect(columns) { viewModel.onColumnsChange(columns) }

        LibraryScreen(
            state = state,
            modifier = modifier,
            onSearchChange = viewModel::onSearchChange,
            onViewModeToggle = viewModel::onViewModeToggle,
            onImportClick = { importLauncher.launch(arrayOf(VIDEO_MIME_PATTERN)) },
            onPickerOpen = viewModel::onPickerOpen,
            onPickerDismiss = viewModel::onPickerDismiss,
            onStatusFilterChange = viewModel::onStatusFilterChange,
            onCategoryFilterChange = viewModel::onCategoryFilterChange,
            onFolderFilterChange = viewModel::onFolderFilterChange,
            onTagFilterChange = viewModel::onTagFilterChange,
            onSortChange = viewModel::onSortChange,
            onClearFilters = viewModel::onClearFilters,
            onOpenVideo = onOpenVideo,
            onDuplicateSkip = viewModel::onDuplicateSkip,
            onDuplicateKeepBoth = viewModel::onDuplicateKeepBoth,
            onDuplicateReplaceRequest = { replaceConfirmVisible = true },
            onDuplicateReplaceDismiss = { replaceConfirmVisible = false },
            onDuplicateReplace = {
                replaceConfirmVisible = false
                viewModel.onDuplicateReplace()
            },
            replaceConfirmVisible = replaceConfirmVisible,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    state: LibraryUiState,
    modifier: Modifier = Modifier,
    onSearchChange: (String) -> Unit = {},
    onViewModeToggle: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onPickerOpen: (LibraryPicker) -> Unit = {},
    onPickerDismiss: () -> Unit = {},
    onStatusFilterChange: (VideoStatus?) -> Unit = {},
    onCategoryFilterChange: (Long?) -> Unit = {},
    onFolderFilterChange: (Long?) -> Unit = {},
    onTagFilterChange: (String?) -> Unit = {},
    onSortChange: (LibrarySort) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
    onDuplicateSkip: () -> Unit = {},
    onDuplicateKeepBoth: () -> Unit = {},
    onDuplicateReplaceRequest: () -> Unit = {},
    onDuplicateReplaceDismiss: () -> Unit = {},
    onDuplicateReplace: () -> Unit = {},
    replaceConfirmVisible: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
    ) {
        Column(Modifier.padding(horizontal = McDimens.SpacingLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                McTextField(
                    value = state.query.text,
                    onValueChange = onSearchChange,
                    label = "Search the library",
                    placeholder = "Title, ID, file name, tag",
                    leadingIcon = Icons.Filled.Search,
                    trailing = {
                        if (state.query.text.isNotEmpty()) {
                            McIconAction(
                                icon = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                onClick = { onSearchChange("") },
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(McDimens.SpacingSm))
                McIconAction(
                    icon = if (state.viewMode == LibraryViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (state.viewMode == LibraryViewMode.GRID) "Show as list" else "Show as grid",
                    onClick = onViewModeToggle,
                )
                McIconAction(
                    icon = Icons.Filled.Add,
                    contentDescription = "Import a video file",
                    onClick = onImportClick,
                    enabled = !state.importing,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(McDimens.SpacingSm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.videos.size} ${if (state.videos.size == 1) "video" else "videos"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.activeFilterCount > 0) {
                    TextButton(onClick = onClearFilters) {
                        Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(McDimens.SpacingXs))
                        Text("Clear ${state.activeFilterCount} filter(s)")
                    }
                }
            }

            if (state.importing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(McDimens.SpacingSm))
                    Text(
                        "Reading the file and allocating a permanent video ID…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = McDimens.SpacingLg),
            horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
        ) {
            item(key = "status-all") {
                McSelectableChip(
                    selected = state.query.status == null,
                    label = "Any status",
                    onClick = { onStatusFilterChange(null) },
                )
            }
            items(VideoStatus.entries.toList(), key = { "status-${it.name}" }) { status ->
                McSelectableChip(
                    selected = state.query.status == status,
                    label = status.label(),
                    onClick = { onStatusFilterChange(if (state.query.status == status) null else status) },
                )
            }
            item(key = "category-picker") {
                McSelectableChip(
                    selected = state.query.categoryId != null,
                    label = state.categoryName(state.query.categoryId)?.let { "Category: $it" } ?: "Category",
                    leadingIcon = Icons.Filled.Category,
                    onClick = { onPickerOpen(LibraryPicker.CATEGORY) },
                )
            }
            item(key = "folder-picker") {
                McSelectableChip(
                    selected = state.query.folderId != null,
                    label = state.folderName(state.query.folderId)?.let { "Folder: $it" } ?: "Folder",
                    leadingIcon = Icons.Filled.Folder,
                    onClick = { onPickerOpen(LibraryPicker.FOLDER) },
                )
            }
            item(key = "tag-picker") {
                McSelectableChip(
                    selected = state.query.tag != null,
                    label = state.query.tag?.let { "Tag: $it" } ?: "Tag",
                    leadingIcon = Icons.Filled.FilterList,
                    onClick = { onPickerOpen(LibraryPicker.TAG) },
                )
            }
            item(key = "sort-picker") {
                McSelectableChip(
                    selected = false,
                    label = state.query.sort.label(),
                    leadingIcon = Icons.Filled.Sort,
                    onClick = { onPickerOpen(LibraryPicker.SORT) },
                )
            }
        }

        when {
            state.loading -> LoadingState(message = "Loading your library…", modifier = Modifier.fillMaxSize())

            state.videos.isEmpty() -> EmptyState(
                icon = if (state.query.hasActiveFilter) Icons.Filled.Search else Icons.Filled.VideoLibrary,
                title = if (state.query.hasActiveFilter) "No videos match these filters" else "Your library is empty",
                message = if (state.query.hasActiveFilter) {
                    "Try a different search term, or clear the filters to see every video again."
                } else {
                    "Import a video file from this device. Master Control reads its metadata, " +
                        "assigns a permanent video ID and prepares a poster in the background."
                },
                actions = if (state.query.hasActiveFilter) {
                    listOf(McAction("Clear filters", onClearFilters, Icons.Filled.Clear))
                } else {
                    listOf(McAction("Import a video", onImportClick, Icons.Filled.Add, emphasized = true))
                },
                modifier = Modifier.fillMaxSize(),
            )

            state.viewMode == LibraryViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(state.columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(McDimens.SpacingLg),
                horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
                verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
            ) {
                items(state.videos, key = { it.videoId }) { video ->
                    VideoGridCard(
                        video = video,
                        state = state,
                        onClick = { onOpenVideo(video.videoId) },
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(McDimens.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
            ) {
                items(state.videos, key = { it.videoId }) { video ->
                    VideoListRow(
                        video = video,
                        state = state,
                        onClick = { onOpenVideo(video.videoId) },
                    )
                }
            }
        }
    }

    when (state.openPicker) {
        LibraryPicker.CATEGORY -> McChoiceDialog(
            title = "Filter by category",
            selectedKey = state.query.categoryId?.toString(),
            options = listOf(McChoice(key = NONE_KEY, label = "Any category")) +
                state.categories.map { McChoice(key = it.categoryId.toString(), label = it.name) },
            onSelect = { choice ->
                onCategoryFilterChange(if (choice.key == NONE_KEY) null else choice.key.toLongOrNull())
                onPickerDismiss()
            },
            onDismiss = onPickerDismiss,
        )

        LibraryPicker.FOLDER -> McChoiceDialog(
            title = "Filter by folder",
            selectedKey = state.query.folderId?.toString(),
            options = listOf(McChoice(key = NONE_KEY, label = "Any folder")) +
                state.folders.map { McChoice(key = it.folderId.toString(), label = it.name) },
            onSelect = { choice ->
                onFolderFilterChange(if (choice.key == NONE_KEY) null else choice.key.toLongOrNull())
                onPickerDismiss()
            },
            onDismiss = onPickerDismiss,
        )

        LibraryPicker.TAG -> McChoiceDialog(
            title = "Filter by tag",
            supportingText = if (state.availableTags.isEmpty()) "No tags are stored yet." else null,
            selectedKey = state.query.tag,
            options = listOf(McChoice(key = NONE_KEY, label = "Any tag")) +
                state.availableTags.map { McChoice(key = it, label = it) },
            onSelect = { choice ->
                onTagFilterChange(if (choice.key == NONE_KEY) null else choice.key)
                onPickerDismiss()
            },
            onDismiss = onPickerDismiss,
        )

        LibraryPicker.SORT -> McChoiceDialog(
            title = "Sort order",
            selectedKey = state.query.sort.name,
            options = LibrarySort.entries.map { sort -> McChoice(key = sort.name, label = sort.label()) },
            onSelect = { choice ->
                LibrarySort.entries.firstOrNull { it.name == choice.key }?.let(onSortChange)
                onPickerDismiss()
            },
            onDismiss = onPickerDismiss,
        )

        // Status filtering lives in the chip row, so no dialog is needed.
        LibraryPicker.STATUS, null -> Unit
    }

    state.duplicate?.let { prompt ->
        DuplicateDialog(
            prompt = prompt,
            state = state,
            onSkip = onDuplicateSkip,
            onKeepBoth = onDuplicateKeepBoth,
            onReplaceRequest = onDuplicateReplaceRequest,
        )
    }

    val prompt = state.duplicate
    if (replaceConfirmVisible && prompt != null) {
        McConfirmDialog(
            title = "Replace the media of ${prompt.existing.videoId}?",
            message = "\"${prompt.existing.title}\" keeps its permanent ID. The picked file is " +
                "uploaded to the same storage channel; the Telegram mapping is updated only after " +
                "the new upload is committed, and the previous message is then removed.",
            confirmLabel = "Replace media",
            dismissLabel = "Back",
            destructive = true,
            acknowledgementLabel = "I understand ${prompt.existing.videoId} keeps its ID and the old message is replaced",
            icon = Icons.Filled.Warning,
            onConfirm = onDuplicateReplace,
            onDismiss = onDuplicateReplaceDismiss,
        )
    }
}

@Composable
private fun VideoGridCard(
    video: Video,
    state: LibraryUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(McDimens.ThumbnailAspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Thumbnail(video = video, modifier = Modifier.matchParentSize())
                if (video.durationMs != null) {
                    StatusChip(
                        text = Formatters.duration(video.durationMs),
                        tone = McTone.NEUTRAL,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(McDimens.SpacingXs),
                    )
                }
                if (video.status == VideoStatus.IMPORTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Column(Modifier.padding(McDimens.SpacingMd), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXs)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = video.videoId, style = MonoLabelStyle, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(text = video.status.label(), tone = video.status.tone(), icon = video.status.icon())
                }
                state.categoryName(video.categoryId)?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListRow(
    video: Video,
    state: LibraryUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(McDimens.SpacingMd), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.width(McDimens.ListThumbnailWidth)
                    .aspectRatio(McDimens.ThumbnailAspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Thumbnail(video = video, modifier = Modifier.matchParentSize())
            }
            Spacer(Modifier.width(McDimens.SpacingMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXxs)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = video.videoId, style = MonoLabelStyle)
                McChipRow {
                    StatusChip(text = video.status.label(), tone = video.status.tone(), icon = video.status.icon())
                    if (video.fileSizeBytes != null) {
                        StatusChip(text = Formatters.bytes(video.fileSizeBytes), tone = McTone.NEUTRAL)
                    }
                    if (video.durationMs != null) {
                        StatusChip(text = Formatters.duration(video.durationMs), tone = McTone.NEUTRAL)
                    }
                }
                val labels = listOfNotNull(
                    state.categoryName(video.categoryId),
                    state.folderName(video.folderId),
                    video.year?.toString(),
                )
                if (labels.isNotEmpty()) {
                    Text(
                        text = labels.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "Added ${DateTimeFormat.relative(video.createdAt.toEpochMilli())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Poster generated at import time.
 *
 * A missing poster renders the placeholder tile behind it rather than an empty
 * hole; no remote image is ever requested.
 */
@Composable
private fun Thumbnail(video: Video, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.VideoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(McDimens.IconSize),
        )
        val model = video.posterUri ?: video.thumbnailUri
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "Poster for ${video.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DuplicateDialog(
    prompt: DuplicatePrompt,
    state: LibraryUiState,
    onSkip: () -> Unit,
    onKeepBoth: () -> Unit,
    onReplaceRequest: () -> Unit,
) {
    val existing = prompt.existing
    AlertDialog(
        onDismissRequest = { if (!state.busy) onSkip() },
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Possible duplicate", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
                SectionHint(prompt.reason)
                DataRow(label = "Existing video", value = existing.videoId, monospaced = true)
                DataRow(label = "Title", value = existing.title)
                DataRow(label = "File name", value = existing.originalFileName)
                DataRow(label = "Size", value = Formatters.bytes(existing.fileSizeBytes))
                DataRow(label = "Status", value = existing.status.label())
                DataRow(
                    label = "Added",
                    value = DateTimeFormat.instant(existing.createdAt),
                )
                state.categoryName(existing.categoryId)?.let { DataRow(label = "Category", value = it) }
                SectionHint(
                    "Keep both assigns a new permanent ID to the picked file. Replace keeps the " +
                        "existing ID and swaps the media after the new upload is committed.",
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onReplaceRequest, enabled = !state.busy) { Text("Replace media") }
                Spacer(Modifier.height(McDimens.SpacingXs))
                OutlinedButton(onClick = onKeepBoth, enabled = !state.busy) { Text("Keep both") }
            }
        },
        dismissButton = { TextButton(onClick = onSkip, enabled = !state.busy) { Text("Skip file") } },
    )
}

private const val NONE_KEY = "any"

internal fun VideoStatus.label(): String = when (this) {
    VideoStatus.IMPORTING -> "Importing"
    VideoStatus.READY -> "Ready"
    VideoStatus.UPLOADING -> "Uploading"
    VideoStatus.COMPLETE -> "In Telegram"
    VideoStatus.FAILED -> "Failed"
}

internal fun VideoStatus.tone(): McTone = when (this) {
    VideoStatus.IMPORTING -> McTone.INFO
    VideoStatus.READY -> McTone.NEUTRAL
    VideoStatus.UPLOADING -> McTone.INFO
    VideoStatus.COMPLETE -> McTone.SUCCESS
    VideoStatus.FAILED -> McTone.DANGER
}

internal fun VideoStatus.icon() = when (this) {
    VideoStatus.IMPORTING -> Icons.Filled.Schedule
    VideoStatus.READY -> Icons.Filled.VideoLibrary
    VideoStatus.UPLOADING -> Icons.Filled.CloudDone
    VideoStatus.COMPLETE -> Icons.Filled.CloudDone
    VideoStatus.FAILED -> Icons.Filled.Error
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
