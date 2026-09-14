package com.mastercontrol.app.data.repository

import androidx.room.withTransaction
import com.mastercontrol.app.core.database.database.MasterControlDatabase
import com.mastercontrol.app.data.local.toDomain
import com.mastercontrol.app.data.local.toEntity
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.Category
import com.mastercontrol.app.domain.model.Folder
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.CategoryRepository
import com.mastercontrol.app.domain.repository.FolderRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val db: MasterControlDatabase,
) : CategoryRepository {

    private val dao get() = db.categoryDao()

    override fun observeAll(): Flow<List<Category>> = dao().observeAll().map { it.map { c -> c.toDomain() } }
    override suspend fun getAllCategories(): List<Category> = dao().getAll().map { it.toDomain() }
    override suspend fun getCategory(categoryId: Long): Category? = dao().get(categoryId)?.toDomain()

    override suspend fun createCategory(category: Category): Category {
        val now = Instant.now()
        val entity = category.copy(categoryId = 0L, createdAt = now, updatedAt = now).toEntity()
        val id = dao().insert(entity)
        return category.copy(categoryId = id, createdAt = now, updatedAt = now)
    }

    override suspend fun updateCategory(category: Category) {
        dao().update(category.copy(updatedAt = Instant.now()).toEntity())
    }

    override suspend fun deleteCategory(categoryId: Long) {
        dao().delete(categoryId) // videos keep row but categoryId nulled (FK SET NULL)
    }

    override suspend fun countCategories(): Int = dao().count()
}

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val db: MasterControlDatabase,
) : FolderRepository {

    private val dao get() = db.folderDao()

    override fun observeAll(): Flow<List<Folder>> = dao().observeAll().map { it.map { f -> f.toDomain() } }
    override suspend fun getAllFolders(): List<Folder> = dao().getAll().map { it.toDomain() }
    override suspend fun getFolder(folderId: Long): Folder? = dao().get(folderId)?.toDomain()

    override suspend fun createFolder(folder: Folder): Folder {
        val now = Instant.now()
        val entity = folder.copy(folderId = 0L, createdAt = now, updatedAt = now).toEntity()
        val id = dao().insert(entity)
        return folder.copy(folderId = id, createdAt = now, updatedAt = now)
    }

    override suspend fun updateFolder(folder: Folder) {
        dao().update(folder.copy(updatedAt = Instant.now()).toEntity())
    }

    override suspend fun deleteFolder(folderId: Long, reassignVideosTo: Long?) {
        db.withTransaction {
            // Videos must keep their permanent IDs; only the folder link changes.
            // null moves them out of every folder (the FK on folders is SET NULL,
            // but we write the target explicitly so the choice is honoured).
            db.videoDao().reassignFolder(
                fromFolderId = folderId,
                targetFolderId = reassignVideosTo,
                updatedAtEpochMs = Instant.now().toEpochMilli(),
            )
            // Child folders become top-level (folders.parentFolderId is SET NULL).
            dao().delete(folderId)
        }
    }

    override suspend fun countFolders(): Int = dao().count()
}

@Singleton
class UploadTaskRepositoryImpl @Inject constructor(
    private val db: MasterControlDatabase,
) : UploadTaskRepository {

    private val dao get() = db.uploadTaskDao()

    override fun observeAll(): Flow<List<UploadTask>> = dao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByState(state: UploadTaskState): Flow<List<UploadTask>> =
        dao().observeByState(state.name).map { list -> list.map { it.toDomain() } }

    override fun observeForVideo(videoId: String): Flow<List<UploadTask>> =
        dao().observeForVideo(videoId).map { list -> list.map { it.toDomain() } }

    override suspend fun nextQueued(): UploadTask? = dao().nextQueued()?.toDomain()

    override suspend fun getTask(taskId: Long): UploadTask? = dao().get(taskId)?.toDomain()
    override suspend fun getTaskByVideo(videoId: String): UploadTask? = dao().getByVideo(videoId)?.toDomain()

    override suspend fun enqueue(task: UploadTask): UploadTask {
        val entity = task.copy(taskId = 0L, createdAt = task.createdAt, updatedAt = task.updatedAt).toEntity()
        val id = dao().insert(entity)
        return task.copy(taskId = id)
    }

    override suspend fun update(task: UploadTask) = dao().update(task.toEntity())
    override suspend fun delete(taskId: Long) = dao().delete(taskId)

    override suspend fun deleteAll(onlyStates: Set<UploadTaskState>?): Int {
        val states = (onlyStates ?: UploadTaskState.entries.map { it.name }).map { it.name }
        return dao().deleteByStates(states)
    }

    override suspend fun getActiveTaskForVideo(videoId: String): UploadTask? =
        dao().getActiveForVideo(videoId)?.toDomain()

    override suspend fun requeueAllFailed(): Int =
        dao().requeueFailed(Instant.now().toEpochMilli())

    override suspend fun reclaimInFlightTasksOlderThan(graceMs: Long): Int {
        val cutoff = Instant.now().minusMillis(graceMs).toEpochMilli()
        return dao().reclaimInFlight(cutoff, Instant.now().toEpochMilli())
    }

    override suspend fun countByState(state: UploadTaskState): Int = dao().countByState(state.name)
    override suspend fun countAll(): Long = dao().countAll()
}

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val db: MasterControlDatabase,
) : ActivityRepository {

    private val dao get() = db.activityDao()

    override fun observeRecent(limit: Int): Flow<List<ActivityLogEntry>> =
        dao().observeRecent(limit.coerceIn(1, 500)).map { list -> list.map { it.toDomain() } }

    override fun observeByType(type: ActivityType?): Flow<List<ActivityLogEntry>> =
        dao().observeFiltered(type?.name, 500).map { list -> list.map { it.toDomain() } }

    override fun observeForVideo(videoId: String, limit: Int): Flow<List<ActivityLogEntry>> =
        dao().observeForVideo(videoId, limit.coerceIn(1, 500)).map { list -> list.map { it.toDomain() } }

    override suspend fun getRecentSnapshot(limit: Int): List<ActivityLogEntry> =
        dao().recentSnapshot(limit.coerceIn(1, 500)).map { it.toDomain() }

    override suspend fun add(entry: ActivityLogEntry): Long {
        val id = dao().insert(entry.toEntity())
        // Bound the log table size (cheap housekeeping).
        if (dao().count() > MAX_ROWS) {
            dao().trimOldest((dao().count() - MAX_ROWS).toInt())
        }
        return id
    }

    override suspend fun count(): Long = dao().count()
    override suspend fun clear() = dao().clear()

    companion object {
        private const val MAX_ROWS = 2000L
    }
}
