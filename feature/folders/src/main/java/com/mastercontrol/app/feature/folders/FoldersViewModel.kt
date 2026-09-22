package com.mastercontrol.app.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.Folder
import com.mastercontrol.app.domain.repository.FolderRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What happens to the videos of a folder that is being deleted. */
enum class FolderDeleteMode(val label: String, val description: String) {
    UNFILE("Leave unfiled", "Videos keep their IDs and Telegram mappings and are no longer in any folder."),
    MOVE("Move to another folder", "Videos keep their IDs and Telegram mappings and are moved to a folder you pick."),
}

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val videoCounts: Map<Long, Int> = emptyMap(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val editor: FolderEditor? = null,
    val parentPickerFor: FolderEditor? = null,
    val pendingDelete: Folder? = null,
    val deleteMode: FolderDeleteMode = FolderDeleteMode.UNFILE,
    val reassignTargetId: Long? = null,
    val reassignPickerOpen: Boolean = false,
    val confirmDelete: Boolean = false,
) {
    fun videoCount(folderId: Long): Int = videoCounts[folderId] ?: 0

    fun childCount(folderId: Long): Int = folders.count { it.parentFolderId == folderId }

    fun parentName(folderId: Long?): String? = folders.firstOrNull { it.folderId == folderId }?.name

    /** Folders eligible as a parent: everything except the folder itself and its descendants. */
    fun parentOptions(editingFolderId: Long?): List<Folder> {
        val excluded = descendantsOf(editingFolderId) + listOfNotNull(editingFolderId)
        return folders.filter { it.folderId !in excluded }
    }

    /** Folders a video can be moved to after deletion (anything but the deleted one). */
    fun reassignOptions(deletingFolderId: Long): List<Folder> =
        folders.filter { it.folderId != deletingFolderId }

    private fun descendantsOf(folderId: Long?): Set<Long> {
        if (folderId == null) return emptySet()
        val result = mutableSetOf<Long>()
        var frontier = listOf(folderId)
        while (frontier.isNotEmpty()) {
            val children = folders.filter { it.parentFolderId in frontier }.map { it.folderId }
            val fresh = children.filter { result.add(it) }
            frontier = fresh
        }
        return result
    }

    /** Depth of a folder in the tree, used for indentation; 0 for top level. */
    fun depthOf(folderId: Long): Int {
        var depth = 0
        var current = folders.firstOrNull { it.folderId == folderId }?.parentFolderId
        val seen = mutableSetOf<Long>()
        while (current != null && seen.add(current) && depth < MAX_TREE_DEPTH) {
            depth += 1
            current = folders.firstOrNull { it.folderId == current }?.parentFolderId
        }
        return depth
    }

    /** Flat display order: parents before children, siblings by name. */
    fun displayOrder(): List<Folder> {
        val sorted = folders.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
        val result = mutableListOf<Folder>()
        val emitted = mutableSetOf<Long>()
        fun emit(folder: Folder) {
            if (!emitted.add(folder.folderId)) return
            result += folder
            sorted.filter { it.parentFolderId == folder.folderId }.forEach { emit(it) }
        }
        sorted.filter { it.parentFolderId == null }.forEach { emit(it) }
        // Orphans (parent row missing) still have to be reachable.
        sorted.filter { !emitted.contains(it.folderId) }.forEach { emit(it) }
        return result
    }

    private companion object {
        const val MAX_TREE_DEPTH = 32
    }
}

data class FolderEditor(
    val folderId: Long?,
    val name: String = "",
    val description: String = "",
    val parentFolderId: Long? = null,
    val nameError: String? = null,
    val saving: Boolean = false,
) {
    val isExisting: Boolean get() = folderId != null
}

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val videoRepository: VideoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoldersUiState())
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                folderRepository.observeAll().collect { folders ->
                    val counts = runCatching { videoRepository.countVideosByFolder() }.getOrDefault(emptyMap())
                    _uiState.update { it.copy(folders = folders, videoCounts = counts, loading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "Folders could not be loaded.",
                    )
                }
            }
        }
    }

    fun onCreateClick() = _uiState.update { it.copy(editor = FolderEditor(folderId = null)) }

    fun onEditClick(folder: Folder) {
        _uiState.update {
            it.copy(
                editor = FolderEditor(
                    folderId = folder.folderId,
                    name = folder.name,
                    description = folder.description,
                    parentFolderId = folder.parentFolderId,
                ),
            )
        }
    }

    fun onEditorNameChange(name: String) =
        _uiState.update { it.copy(editor = it.editor?.copy(name = name, nameError = null)) }

    fun onEditorDescriptionChange(description: String) =
        _uiState.update { it.copy(editor = it.editor?.copy(description = description)) }

    fun onParentPickerOpen() = _uiState.update { it.copy(parentPickerFor = it.editor) }

    fun onParentPickerDismiss() = _uiState.update { it.copy(parentPickerFor = null) }

    fun onParentSelected(folderId: Long?) {
        _uiState.update {
            it.copy(editor = it.editor?.copy(parentFolderId = folderId, nameError = null), parentPickerFor = null)
        }
    }

    fun onDismissEditor() = _uiState.update { it.copy(editor = null) }

    fun onSaveEditor() {
        val editor = _uiState.value.editor ?: return
        val name = editor.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(editor = editor.copy(nameError = "Enter a folder name.")) }
            return
        }
        val duplicate = _uiState.value.folders.any {
            it.name.equals(name, ignoreCase = true) && it.folderId != editor.folderId
        }
        if (duplicate) {
            _uiState.update { it.copy(editor = editor.copy(nameError = "A folder with this name already exists.")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(editor = editor.copy(saving = true), busy = true) }
            try {
                val existing = editor.folderId?.let { id -> _uiState.value.folders.firstOrNull { it.folderId == id } }
                if (existing != null) {
                    folderRepository.updateFolder(
                        existing.copy(
                            name = name,
                            description = editor.description.trim(),
                            parentFolderId = editor.parentFolderId,
                        ),
                    )
                } else {
                    folderRepository.createFolder(
                        Folder(
                            folderId = 0L,
                            name = name,
                            parentFolderId = editor.parentFolderId,
                            description = editor.description.trim(),
                            sortOrder = 0,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                        ),
                    )
                }
                _uiState.update {
                    it.copy(
                        editor = null,
                        busy = false,
                        infoMessage = if (existing != null) "Folder updated." else "Folder \"$name\" created.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        editor = editor.copy(saving = false),
                        busy = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The folder could not be saved.",
                    )
                }
            }
        }
    }

    fun onDeleteClick(folder: Folder) {
        _uiState.update {
            it.copy(
                pendingDelete = folder,
                deleteMode = FolderDeleteMode.UNFILE,
                reassignTargetId = null,
                reassignPickerOpen = false,
                confirmDelete = false,
            )
        }
    }

    fun onDeleteModeChange(mode: FolderDeleteMode) = _uiState.update {
        it.copy(deleteMode = mode, reassignTargetId = if (mode == FolderDeleteMode.MOVE) null else null)
    }

    fun onReassignPickerOpen() = _uiState.update { it.copy(reassignPickerOpen = true) }

    fun onReassignPickerDismiss() = _uiState.update { it.copy(reassignPickerOpen = false) }

    fun onReassignTargetSelected(folderId: Long) =
        _uiState.update { it.copy(reassignTargetId = folderId, reassignPickerOpen = false) }

    fun onDeleteReview() = _uiState.update { it.copy(confirmDelete = true) }

    fun onDismissDelete() = _uiState.update { it.copy(pendingDelete = null, confirmDelete = false) }

    fun onConfirmDelete() {
        val folder = _uiState.value.pendingDelete ?: return
        val mode = _uiState.value.deleteMode
        val target = _uiState.value.reassignTargetId
        if (mode == FolderDeleteMode.MOVE && target == null) {
            _uiState.update { it.copy(confirmDelete = false, errorMessage = "Choose the folder to move the videos into.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                folderRepository.deleteFolder(
                    folderId = folder.folderId,
                    reassignVideosTo = if (mode == FolderDeleteMode.MOVE) target else null,
                )
                val movedTo = if (mode == FolderDeleteMode.MOVE) _uiState.value.parentName(target) else null
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingDelete = null,
                        confirmDelete = false,
                        infoMessage = if (movedTo != null) {
                            "\"${folder.name}\" was deleted; its videos were moved to \"$movedTo\"."
                        } else {
                            "\"${folder.name}\" was deleted; its videos are now unfiled."
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingDelete = null,
                        confirmDelete = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The folder could not be deleted.",
                    )
                }
            }
        }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
}
