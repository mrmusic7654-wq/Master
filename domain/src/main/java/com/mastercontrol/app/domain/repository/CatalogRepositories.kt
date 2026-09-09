package com.mastercontrol.app.domain.repository

import com.mastercontrol.app.domain.model.Category
import com.mastercontrol.app.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAllCategories(): List<Category>
    suspend fun getCategory(categoryId: Long): Category?
    suspend fun createCategory(category: Category): Category
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(categoryId: Long)
    suspend fun countCategories(): Int
}

interface FolderRepository {
    fun observeAll(): Flow<List<Folder>>
    suspend fun getAllFolders(): List<Folder>
    suspend fun getFolder(folderId: Long): Folder?
    suspend fun createFolder(folder: Folder): Folder
    suspend fun updateFolder(folder: Folder)
    suspend fun deleteFolder(folderId: Long, reassignVideosTo: Long? = null)
    suspend fun countFolders(): Int
}
