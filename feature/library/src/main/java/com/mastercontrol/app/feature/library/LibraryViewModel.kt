package com.mastercontrol.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.Category
import com.mastercontrol.app.domain.model.Folder
import com.mastercontrol.app.domain.model.LibraryQuery
import com.mastercontrol.app.domain.model.LibrarySort
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.repository.CategoryRepository
import com.mastercontrol.app.domain.repository.FolderRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import com.mastercontrol.app.domain.usecase.ImportOutcome
import com.mastercontrol.app.domain.usecase.PrepareVideoImportUseCase
import com.mastercontrol.app.domain.usecase.ReplaceVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Grid or list rendering of the catalog. A view preference, not data. */
enum class LibraryViewMode { GRID, LIST }

/** Which single-select picker is open. */
enum class LibraryPicker { STATUS, CATEGORY, FOLDER, TAG, SORT }

/** A file that looks like one already in the catalog. */
data class DuplicatePrompt(
    val existing: Video,
    val pickedUri: String,
    val reason: String,
)

data class LibraryUiState(
    val videos: List<Video> = emptyList(),
    val query: LibraryQuery = LibraryQuery(),
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val categories: List<Category> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val importing: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val duplicate: DuplicatePrompt? = null,
    val openPicker: LibraryPicker? = null,
    val columns: Int = DEFAULT_COLUMNS,
) {
    fun categoryName(categoryId: Long?): String? =
        categories.firstOrNull { it.categoryId == categoryId }?.name

    fun folderName(folderId: Long?): String? = folders.firstOrNull { it.folderId == folderId }?.name

    val activeFilterCount: Int
        get() = listOfNotNull(
            query.text.ifBlank { null },
            query.categoryId,
            query.folderId,
            query.status,
            query.tag,
        ).size

    companion object {
        const val DEFAULT_COLUMNS = 2
    }
}

/**
 * Media library: search, filter, sort and import.
 *
 * Importing reads the picked file through SAF, allocates a permanent video ID
 * transactionally and hands the heavy work (hash, poster) to a worker, so the
 * list never blocks. A file that looks like an existing one is offered as a
 * choice — skip, keep both, or replace the existing video, which keeps its
 * permanent ID and only changes the Telegram mapping later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    categoryRepository: CategoryRepository,
    folderRepository: FolderRepository,
    private val prepareImport: PrepareVideoImportUseCase,
    private val replaceVideo: ReplaceVideoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow(LibraryQuery())
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            query.flatMapLatest { active -> videoRepository.observeAll(active) }.collect { videos ->
                _uiState.update { it.copy(videos = videos, query = query.value, loading = false) }
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
        viewModelScope.launch {
            videoRepository.observeAllTags().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
    }

    /** Window class drives the column count; the screen reports it on layout. */
    fun onColumnsChange(columns: Int) {
        if (columns < 1) return
        _uiState.update { if (it.columns == columns) it else it.copy(columns = columns) }
    }

    fun onViewModeToggle() = _uiState.update {
        it.copy(viewMode = if (it.viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID)
    }

    fun onSearchChange(text: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            query.update { it.copy(text = text) }
        }
    }

    fun onStatusFilterChange(status: VideoStatus?) = applyFilter { it.copy(status = status) }

    fun onCategoryFilterChange(categoryId: Long?) = applyFilter { it.copy(categoryId = categoryId) }

    fun onFolderFilterChange(folderId: Long?) = applyFilter { it.copy(folderId = folderId) }

    fun onTagFilterChange(tag: String?) = applyFilter { it.copy(tag = tag) }

    fun onSortChange(sort: LibrarySort) = applyFilter { it.copy(sort = sort) }

    fun onClearFilters() {
        searchJob?.cancel()
        val sort = query.value.sort
        query.value = LibraryQuery(sort = sort)
    }

    fun onPickerOpen(picker: LibraryPicker) = _uiState.update { it.copy(openPicker = picker) }

    fun onPickerDismiss() = _uiState.update { it.copy(openPicker = null) }

    private fun applyFilter(transform: (LibraryQuery) -> LibraryQuery) {
        query.update(transform)
    }

    /** Called by the SAF picker result in the screen. */
    fun onVideoPicked(uri: String) {
        if (uri.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No file was selected.") }
            return
        }
        runImport(uri, allowDuplicate = false)
    }

    fun onDuplicateSkip() = _uiState.update { it.copy(duplicate = null) }

    /** Imports the picked file as its own catalog entry with a new permanent ID. */
    fun onDuplicateKeepBoth() {
        val prompt = _uiState.value.duplicate ?: return
        _uiState.update { it.copy(duplicate = null) }
        runImport(prompt.pickedUri, allowDuplicate = true)
    }

    /**
     * Replaces the media of the existing video with the picked file.
     *
     * The permanent video ID survives; the replacement is uploaded and only then
     * does the mapping change, so the catalog never points at media that is not
     * in Telegram yet.
     */
    fun onDuplicateReplace() {
        val prompt = _uiState.value.duplicate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, duplicate = null) }
            try {
                val task = replaceVideo(prompt.existing.videoId, prompt.pickedUri, channelId = null)
                _uiState.update {
                    it.copy(
                        busy = false,
                        infoMessage = "${prompt.existing.videoId} keeps its ID; the replacement is queued " +
                            "(task ${task.taskId}) and the mapping updates when it completes.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The video could not be replaced.",
                    )
                }
            }
        }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun runImport(uri: String, allowDuplicate: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, busy = true) }
            try {
                when (val outcome = prepareImport(uri, allowDuplicate = allowDuplicate)) {
                    is ImportOutcome.Imported -> _uiState.update {
                        it.copy(
                            importing = false,
                            busy = false,
                            infoMessage = "${outcome.video.videoId} was imported as " +
                                "\"${outcome.video.originalFileName}\". Poster and checksum are prepared in the background.",
                        )
                    }

                    is ImportOutcome.Duplicate -> _uiState.update {
                        it.copy(
                            importing = false,
                            busy = false,
                            duplicate = DuplicatePrompt(
                                existing = outcome.duplicateOf,
                                pickedUri = uri,
                                reason = outcome.reason,
                            ),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(importing = false, busy = false) }
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        importing = false,
                        busy = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The file could not be imported.",
                    )
                }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
