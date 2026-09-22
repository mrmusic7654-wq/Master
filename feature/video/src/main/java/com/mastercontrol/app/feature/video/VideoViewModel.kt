package com.mastercontrol.app.feature.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.Category
import com.mastercontrol.app.domain.model.Folder
import com.mastercontrol.app.domain.model.MessageVerification
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.CategoryRepository
import com.mastercontrol.app.domain.repository.FolderRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import com.mastercontrol.app.domain.usecase.DeleteVideoUseCase
import com.mastercontrol.app.domain.usecase.EditVideoMetadataUseCase
import com.mastercontrol.app.domain.usecase.QueueUploadUseCase
import com.mastercontrol.app.domain.usecase.ReplaceVideoUseCase
import com.mastercontrol.app.domain.usecase.SetVideoThumbnailUseCase
import com.mastercontrol.app.domain.usecase.VerifyVideoMappingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Metadata editor values as text, so an empty field is distinguishable from "unchanged". */
data class VideoEditor(
    val title: String,
    val description: String,
    val yearText: String,
    val language: String,
    val ratingText: String,
    val releaseDate: String,
    val tagsText: String,
    val categoryId: Long?,
    val folderId: Long?,
    val titleError: String? = null,
    val yearError: String? = null,
    val ratingError: String? = null,
    val saving: Boolean = false,
)

data class VideoUiState(
    val videoId: String,
    val video: Video? = null,
    val mapping: TelegramMapping? = null,
    val tasks: List<UploadTask> = emptyList(),
    val history: List<ActivityLogEntry> = emptyList(),
    val channels: List<StorageChannel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val notFound: Boolean = false,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val verifying: Boolean = false,
    val verification: MessageVerification? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val editor: VideoEditor? = null,
    val deleteDialogOpen: Boolean = false,
    val deleteMode: DeleteVideoUseCase.Mode = DeleteVideoUseCase.Mode.LOCAL_ONLY,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    /** Replacement file waiting for a channel choice (only when no mapping exists). */
    val pendingReplacementUri: String? = null,
    val uploadChannelPickerOpen: Boolean = false,
) {
    fun channelName(channelId: Long?): String? =
        channels.firstOrNull { it.id == channelId }?.displayName

    fun categoryName(categoryId: Long?): String? =
        categories.firstOrNull { it.categoryId == categoryId }?.name

    fun folderName(folderId: Long?): String? = folders.firstOrNull { it.folderId == folderId }?.name

    val activeTask: UploadTask?
        get() = tasks.firstOrNull { it.state != UploadTaskState.COMPLETED && it.state != UploadTaskState.CANCELLED }

    val canQueueUpload: Boolean
        get() {
            val video = video ?: return false
            return activeTask == null &&
                channels.any { it.enabled && it.permissions.canUploadVideos } &&
                video.status != VideoStatus.IMPORTING
        }

    val canReplaceMedia: Boolean
        get() = video != null && activeTask == null &&
            channels.any { it.enabled && it.permissions.canUploadVideos }

    val hasTelegramMedia: Boolean
        get() = mapping != null && mapping?.channelId != null
}

/**
 * Video details: the permanent ID, its Telegram mapping, its upload tasks and
 * its real history.
 *
 * Every action here is explicit about what it touches. Deleting from Telegram
 * is a separate, acknowledged choice; replacing media keeps the permanent ID
 * and only rewrites the mapping after the new upload is committed.
 */
@HiltViewModel
class VideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    uploadTaskRepository: UploadTaskRepository,
    activityRepository: ActivityRepository,
    channelRepository: TelegramChannelRepository,
    categoryRepository: CategoryRepository,
    folderRepository: FolderRepository,
    private val editMetadata: EditVideoMetadataUseCase,
    private val queueUpload: QueueUploadUseCase,
    private val replaceVideo: ReplaceVideoUseCase,
    private val deleteVideo: DeleteVideoUseCase,
    private val verifyMapping: VerifyVideoMappingUseCase,
    private val setThumbnail: SetVideoThumbnailUseCase,
) : ViewModel() {

    private val videoId: String = savedStateHandle[VideoNavigation.ARG_VIDEO_ID] ?: ""

    private val _uiState = MutableStateFlow(VideoUiState(videoId = videoId))
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    init {
        if (videoId.isBlank()) {
            _uiState.update { it.copy(loading = false, notFound = true) }
        } else {
            viewModelScope.launch {
                videoRepository.observeVideo(videoId).collect { video ->
                    _uiState.update {
                        it.copy(video = video, loading = false, notFound = video == null)
                    }
                }
            }
            viewModelScope.launch {
                videoRepository.observeMapping(videoId).collect { mapping ->
                    _uiState.update { it.copy(mapping = mapping) }
                }
            }
            viewModelScope.launch {
                uploadTaskRepository.observeForVideo(videoId).collect { tasks ->
                    _uiState.update { it.copy(tasks = tasks) }
                }
            }
            viewModelScope.launch {
                activityRepository.observeForVideo(videoId).collect { history ->
                    _uiState.update { it.copy(history = history) }
                }
            }
            viewModelScope.launch {
                channelRepository.observeChannels().collect { channels ->
                    _uiState.update { it.copy(channels = channels) }
                }
            }
            viewModelScope.launch {
                categoryRepository.observeAll().collect { categories ->
                    _uiState.update { it.copy(categories = categories) }
                }
            }
            viewModelScope.launch {
                folderRepository.observeAll().collect { folders ->
                    _uiState.update { it.copy(folders = folders) }
                }
            }
        }
    }

    // ---- metadata editing -------------------------------------------------

    fun onEditClick() {
        val video = _uiState.value.video ?: return
        _uiState.update {
            it.copy(
                editor = VideoEditor(
                    title = video.title,
                    description = video.description,
                    yearText = video.year?.toString() ?: "",
                    language = video.language ?: "",
                    ratingText = video.rating?.let { rating -> trimRating(rating) } ?: "",
                    releaseDate = video.releaseDate ?: "",
                    tagsText = video.tags.joinToString(", "),
                    categoryId = video.categoryId,
                    folderId = video.folderId,
                ),
            )
        }
    }

    fun onDismissEditor() = _uiState.update { it.copy(editor = null) }

    fun onEditorChange(transform: (VideoEditor) -> VideoEditor) =
        _uiState.update { state -> state.copy(editor = state.editor?.let(transform)) }

    fun onSaveEditor() {
        val editor = _uiState.value.editor ?: return
        val video = _uiState.value.video ?: return

        val title = editor.title.trim()
        if (title.isEmpty()) {
            onEditorChange { it.copy(titleError = "A title is required.") }
            return
        }
        val year = editor.yearText.trim().let { text ->
            if (text.isEmpty()) {
                null
            } else {
                text.toIntOrNull()?.also {
                    if (it < MIN_YEAR || it > MAX_YEAR) {
                        onEditorChange { e -> e.copy(yearError = "Enter a year between $MIN_YEAR and $MAX_YEAR.") }
                        return
                    }
                } ?: run {
                    onEditorChange { e -> e.copy(yearError = "Enter a four-digit year, or leave it empty.") }
                    return
                }
            }
        }
        val rating = editor.ratingText.trim().let { text ->
            if (text.isEmpty()) {
                null
            } else {
                text.replace(',', '.').toFloatOrNull()?.also {
                    if (it < 0f || it > MAX_RATING) {
                        onEditorChange { e -> e.copy(ratingError = "Enter a rating between 0 and ${MAX_RATING.toInt()}.") }
                        return
                    }
                } ?: run {
                    onEditorChange { e -> e.copy(ratingError = "Enter a number, or leave the rating empty.") }
                    return
                }
            }
        }

        runGuarded("The metadata could not be saved.") {
            editMetadata(
                video = video,
                title = title,
                description = editor.description,
                categoryId = editor.categoryId,
                folderId = editor.folderId,
                tags = editor.tagsText.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() },
                year = year,
                language = editor.language.trim().ifEmpty { null },
                rating = rating,
                releaseDate = editor.releaseDate.trim().ifEmpty { null },
            )
            _uiState.update { it.copy(editor = null, infoMessage = "${video.videoId} metadata saved.") }
        }
    }

    // ---- uploads ----------------------------------------------------------

    fun onQueueUploadClick() = _uiState.update { it.copy(uploadChannelPickerOpen = true) }

    fun onDismissUploadChannelPicker() = _uiState.update { it.copy(uploadChannelPickerOpen = false) }

    fun onUploadChannelSelected(channelId: Long) {
        _uiState.update { it.copy(uploadChannelPickerOpen = false) }
        runGuarded("The upload could not be queued.") {
            val task = queueUpload(videoId, channelId)
            _uiState.update {
                it.copy(infoMessage = "${task.videoId} queued for upload (task ${task.taskId}). It runs in the background.")
            }
        }
    }

    fun onReplacementPicked(uri: String) {
        if (uri.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No replacement file was selected.") }
            return
        }
        val state = _uiState.value
        if (state.mapping != null) {
            startReplacement(uri, channelId = null)
        } else {
            _uiState.update { it.copy(pendingReplacementUri = uri) }
        }
    }

    fun onDismissReplacement() = _uiState.update { it.copy(pendingReplacementUri = null) }

    fun onReplacementChannelSelected(channelId: Long) {
        val uri = _uiState.value.pendingReplacementUri ?: return
        _uiState.update { it.copy(pendingReplacementUri = null) }
        startReplacement(uri, channelId)
    }

    private fun startReplacement(uri: String, channelId: Long?) {
        runGuarded("The replacement could not be queued.") {
            val task = replaceVideo(videoId, uri, channelId)
            _uiState.update {
                it.copy(
                    infoMessage = "$videoId keeps its permanent ID. The replacement is queued " +
                        "(task ${task.taskId}); the mapping updates when the new upload is committed.",
                )
            }
        }
    }

    fun onPosterPicked(uri: String) {
        if (uri.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No image was selected.") }
            return
        }
        runGuarded("The poster could not be changed.") {
            setThumbnail(videoId, uri)
            _uiState.update { it.copy(infoMessage = "Poster updated for $videoId. Telegram media is unchanged.") }
        }
    }

    // ---- verification -----------------------------------------------------

    fun onVerifyMappingClick() {
        viewModelScope.launch {
            _uiState.update { it.copy(verifying = true) }
            try {
                val result = verifyMapping(videoId)
                _uiState.update {
                    it.copy(
                        verifying = false,
                        verification = result,
                        infoMessage = when {
                            !result.found -> "The Telegram message no longer exists. The mapping is marked as remote-deleted."
                            !result.matchesVideo -> "The Telegram media changed; the mapping is marked stale."
                            else -> "Telegram confirms this mapping is intact."
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        verifying = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The mapping could not be verified.",
                    )
                }
            }
        }
    }

    // ---- deletion ---------------------------------------------------------

    fun onDeleteClick() = _uiState.update {
        it.copy(deleteDialogOpen = true, deleteMode = DeleteVideoUseCase.Mode.LOCAL_ONLY, confirmDelete = false)
    }

    fun onDismissDelete() = _uiState.update { it.copy(deleteDialogOpen = false, confirmDelete = false) }

    fun onDeleteModeChange(mode: DeleteVideoUseCase.Mode) = _uiState.update { it.copy(deleteMode = mode) }

    fun onDeleteReview() = _uiState.update { it.copy(confirmDelete = true) }

    fun onConfirmDelete() {
        val mode = _uiState.value.deleteMode
        runGuarded("The video could not be deleted.") {
            deleteVideo(videoId, mode)
            _uiState.update {
                it.copy(
                    deleteDialogOpen = false,
                    confirmDelete = false,
                    deleted = true,
                    infoMessage = if (mode == DeleteVideoUseCase.Mode.LOCAL_AND_TELEGRAM) {
                        "$videoId was deleted from this device and from Telegram."
                    } else {
                        "$videoId was deleted from this device. Telegram media was left untouched."
                    },
                )
            }
        }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun runGuarded(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                block()
                _uiState.update { it.copy(busy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(busy = false, errorMessage = (t as? AppError)?.userMessage ?: failureMessage)
                }
            }
        }
    }

    private fun trimRating(rating: Float): String {
        val scaled = rating * 10f
        val rounded = kotlin.math.round(scaled) / 10f
        return if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()
    }

    private companion object {
        const val MIN_YEAR = 1800
        const val MAX_YEAR = 2200
        const val MAX_RATING = 5f
    }
}
