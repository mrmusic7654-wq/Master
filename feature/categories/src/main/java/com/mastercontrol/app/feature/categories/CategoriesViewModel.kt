package com.mastercontrol.app.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.Category
import com.mastercontrol.app.domain.repository.CategoryRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val videoCounts: Map<Long, Int> = emptyMap(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val editor: CategoryEditor? = null,
    val pendingDelete: Category? = null,
)

data class CategoryEditor(
    val categoryId: Long?,
    val name: String = "",
    val description: String = "",
    val icon: CategoryIcon = CategoryIcon.GENERIC,
    val nameError: String? = null,
    val saving: Boolean = false,
) {
    val isExisting: Boolean get() = categoryId != null
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val videoRepository: VideoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                categoryRepository.observeAll().collect { categories ->
                    val counts = runCatching { videoRepository.countVideosByCategory() }.getOrDefault(emptyMap())
                    _uiState.update { it.copy(categories = categories, videoCounts = counts, loading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "Categories could not be loaded.",
                    )
                }
            }
        }
    }

    fun onCreateClick() = _uiState.update { it.copy(editor = CategoryEditor(categoryId = null)) }

    fun onEditClick(category: Category) {
        _uiState.update {
            it.copy(
                editor = CategoryEditor(
                    categoryId = category.categoryId,
                    name = category.name,
                    description = category.description.orEmpty(),
                    icon = CategoryIcon.fromKey(category.icon),
                ),
            )
        }
    }

    fun onEditorNameChange(name: String) =
        _uiState.update { it.copy(editor = it.editor?.copy(name = name, nameError = null)) }

    fun onEditorDescriptionChange(description: String) =
        _uiState.update { it.copy(editor = it.editor?.copy(description = description)) }

    fun onEditorIconChange(icon: CategoryIcon) = _uiState.update { it.copy(editor = it.editor?.copy(icon = icon)) }

    fun onDismissEditor() = _uiState.update { it.copy(editor = null) }

    fun onSaveEditor() {
        val editor = _uiState.value.editor ?: return
        val name = editor.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(editor = editor.copy(nameError = "Enter a category name.")) }
            return
        }
        val duplicate = _uiState.value.categories.any {
            it.name.equals(name, ignoreCase = true) && it.categoryId != editor.categoryId
        }
        if (duplicate) {
            _uiState.update { it.copy(editor = editor.copy(nameError = "A category with this name already exists.")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(editor = editor.copy(saving = true), busy = true) }
            try {
                val existing = editor.categoryId?.let { id ->
                    _uiState.value.categories.firstOrNull { it.categoryId == id }
                }
                if (existing != null) {
                    categoryRepository.updateCategory(
                        existing.copy(
                            name = name,
                            description = editor.description.trim(),
                            icon = editor.icon.key,
                        ),
                    )
                } else {
                    categoryRepository.createCategory(
                        Category(
                            categoryId = 0L,
                            name = name,
                            description = editor.description.trim(),
                            icon = editor.icon.key,
                            sortOrder = 0,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                        ),
                    )
                }
                _uiState.update {
                    it.copy(editor = null, busy = false, infoMessage = if (editor.isExisting) "Category updated." else "Category created.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        editor = editor.copy(saving = false),
                        busy = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The category could not be saved.",
                    )
                }
            }
        }
    }

    fun onDeleteClick(category: Category) = _uiState.update { it.copy(pendingDelete = category) }

    fun onDismissDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun onConfirmDelete() {
        val category = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                categoryRepository.delete(category.categoryId)
                _uiState.update {
                    it.copy(busy = false, pendingDelete = null, infoMessage = "\"${category.name}\" was deleted. Its videos are kept and are now uncategorized.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingDelete = null,
                        errorMessage = (t as? AppError)?.userMessage ?: "The category could not be deleted.",
                    )
                }
            }
        }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
}
