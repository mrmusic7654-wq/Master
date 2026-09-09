package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.CatalogBackup
import com.mastercontrol.app.domain.model.Category
import com.mastercontrol.app.domain.model.CategoryBackup
import com.mastercontrol.app.domain.model.Folder
import com.mastercontrol.app.domain.model.FolderBackup
import com.mastercontrol.app.domain.model.ImportConflictKind
import com.mastercontrol.app.domain.model.ImportMode
import com.mastercontrol.app.domain.model.ImportPreview
import com.mastercontrol.app.domain.model.ImportPreviewItem
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.StreamerCatalogItem
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.CategoryRepository
import com.mastercontrol.app.domain.repository.FolderRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * JSON catalog export/import. Secrets (API hash, session keys, verification
 * codes) are structurally excluded from every serializable type here.
 */
class ExportCatalogUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val folderRepository: FolderRepository,
    private val activityRepository: ActivityRepository,
    private val json: Json,
) {
    suspend operator fun invoke(): String {
        val videos = videoRepository.getAllVideos()
        val allCategories = categoryRepository.getAllCategories()
        val allFolders = folderRepository.getAllFolders()
        val allMappings = videoRepository.getAllMappings().associateBy { it.videoId }

        val categoryName = allCategories.associate { it.categoryId to it.name }
        val folderName = allFolders.associate { it.folderId to it.name }

        val items = videos.map { video ->
            val mapping = allMappings[video.videoId]
            StreamerCatalogItem(
                videoId = video.videoId,
                title = video.title,
                description = video.description,
                thumbnailReference = video.thumbnailUri,
                durationMs = video.durationMs,
                fileSizeBytes = video.fileSizeBytes,
                mimeType = video.mimeType,
                width = video.width,
                height = video.height,
                category = video.categoryId?.let { categoryName[it] },
                folder = video.folderId?.let { folderName[it] },
                tags = video.tags,
                telegramChannelId = mapping?.channelId,
                telegramMessageId = mapping?.messageId,
                metadata = buildMap {
                    put("year", video.year?.toString() ?: "")
                    put("language", video.language ?: "")
                    put("rating", video.rating?.toString() ?: "")
                    put("originalFileName", video.originalFileName)
                    put("status", video.status.name)
                }.filterValues { it.isNotEmpty() },
            )
        }

        val backup = CatalogBackup(
            exportedAt = Instant.now().toString(),
            categories = allCategories.map { CategoryBackup(it.name, it.description, it.icon, it.sortOrder) },
            folders = allFolders.map { FolderBackup(it.name, it.parentFolderId?.let { p -> folderName[p] }, it.description, it.sortOrder) },
            items = items,
        )
        val output = json.encodeToString(CatalogBackup.serializer(), backup)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.BACKUP_EXPORTED,
                message = "Catalog exported (${items.size} items)",
                createdAt = Instant.now(),
            ),
        )
        return output
    }
}

/** Parses and validates a backup and previews conflicts without writing anything. */
class PreviewCatalogImportUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val folderRepository: FolderRepository,
    private val json: Json,
) {
    suspend operator fun invoke(jsonText: String): ImportPreview {
        val backup = try {
            json.decodeFromString(CatalogBackup.serializer(), jsonText)
        } catch (t: Throwable) {
            return ImportPreview(emptyList(), emptyList(), emptyList(), valid = false, invalidReason = "Not a valid Master Control catalog backup (${t.message}).")
        }
        if (backup.format != "master-control-catalog" || backup.formatVersion > 1) {
            return ImportPreview(emptyList(), emptyList(), emptyList(), valid = false, invalidReason = "Unsupported backup format/version.")
        }
        val existingVideos = videoRepository.getAllVideos().associateBy { it.videoId }
        val existingTitles = videoRepository.getAllVideos().map { it.title.trim().lowercase() }.toSet()
        val categories = categoryRepository.getAllCategories().map { it.name }
        val folders = folderRepository.getAllFolders().map { it.name }

        val items = backup.items.map { item ->
            val conflict = when {
                existingVideos.containsKey(item.videoId) ->
                    ImportConflictKind.VIDEO_ID_EXISTS to "Video ID ${item.videoId} already exists in the catalog."
                item.title.trim().lowercase() in existingTitles ->
                    ImportConflictKind.TITLE_MATCH to "A video titled \"${item.title}\" already exists."
                else -> ImportConflictKind.NONE to null
            }
            ImportPreviewItem(item, conflict.first, conflict.second)
        }
        return ImportPreview(categories, folders, items, valid = true)
    }
}

/** Applies a validated import. Conflicts are skipped unless [mode] allows replacement. */
class ImportCatalogUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val folderRepository: FolderRepository,
    private val activityRepository: ActivityRepository,
    private val json: Json,
) {
    suspend operator fun invoke(jsonText: String, mode: ImportMode): ImportPreview {
        val preview = PreviewCatalogImportUseCase(videoRepository, categoryRepository, folderRepository, json).invoke(jsonText)
        if (!preview.valid) throw AppError.CatalogImportError(preview.invalidReason ?: "invalid backup")
        val backup = json.decodeFromString(CatalogBackup.serializer(), jsonText)
        val now = Instant.now()

        // Categories/folders upsert by name (import never deletes anything).
        val categoryIds = mutableMapOf<String, Long>()
        backup.categories.forEach { cat ->
            val existing = categoryRepository.getAllCategories().firstOrNull { it.name == cat.name }
            val id = existing?.categoryId
                ?: categoryRepository.createCategory(
                    Category(categoryId = 0L, name = cat.name, description = cat.description, icon = cat.icon, sortOrder = cat.sortOrder, createdAt = now, updatedAt = now),
                ).categoryId
            categoryIds[cat.name] = id
        }
        val folderNameToId = mutableMapOf<String, Long?>()
        val foldersByName = backup.folders.associateBy { it.name }
        // Parent folders first (single level nesting).
        backup.folders.filter { it.parentName == null }.forEach { createFolderIfMissing(it, folderNameToId, now) }
        backup.folders.filter { it.parentName != null }.forEach { createFolderIfMissing(it, folderNameToId, now) }

        var imported = 0
        var skipped = 0
        for (item in preview.items) {
            if (item.conflict == ImportConflictKind.VIDEO_ID_EXISTS && mode == ImportMode.REPLACE_CONFLICTS) {
                // Replace metadata only; remote media is never touched by import.
                val existing = videoRepository.getVideo(item.streamerItem.videoId)
                if (existing != null) {
                    videoRepository.updateVideo(existing.withBackupMetadata(item.streamerItem, categoryIds, folderNameToId, now))
                }
                imported++
                continue
            }
            if (item.conflict != ImportConflictKind.NONE) {
                skipped++
                continue
            }
            val s = item.streamerItem
            val videoId = s.videoId.takeIf { it.isNotBlank() } ?: continue
            val video = Video(
                videoId = videoId,
                title = s.title.ifBlank { videoId },
                originalFileName = s.metadata["originalFileName"] ?: s.title,
                description = s.description,
                durationMs = s.durationMs,
                fileSizeBytes = s.fileSizeBytes,
                mimeType = s.mimeType,
                width = s.width,
                height = s.height,
                sourceUri = "", // imported backups do not carry media; re-import source separately
                thumbnailUri = s.thumbnailReference,
                year = s.metadata["year"]?.toIntOrNull(),
                language = s.metadata["language"]?.takeIf { it.isNotBlank() },
                rating = s.metadata["rating"]?.toFloatOrNull(),
                createdAt = now,
                updatedAt = now,
                status = if (s.telegramMessageId != null) VideoStatus.COMPLETE else VideoStatus.READY,
                categoryId = s.category?.let { categoryIds[it] },
                folderId = s.folder?.let { folderNameToId[it] },
                tags = s.tags,
            )
            videoRepository.createVideo(video)
            if (s.telegramChannelId != null && s.telegramMessageId != null) {
                videoRepository.commitCompletedUpload(
                    videoId = videoId,
                    mapping = TelegramMapping(
                        videoId = videoId,
                        channelId = s.telegramChannelId,
                        messageId = s.telegramMessageId,
                        fileSizeBytes = s.fileSizeBytes,
                        mimeType = s.mimeType,
                        uploadedAt = now,
                        updatedAt = now,
                        mappingStatus = MappingStatus.PENDING_VERIFY,
                    ),
                    taskId = null,
                )
            }
            imported++
        }
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.BACKUP_IMPORTED,
                message = "Catalog imported ($imported items, $skipped skipped)",
                createdAt = now,
            ),
        )
        return ImportPreview(preview.categories, preview.folders, preview.items, valid = true)
    }

    private suspend fun createFolderIfMissing(fb: FolderBackup, into: MutableMap<String, Long?>, now: Instant) {
        val existing = folderRepository.getAllFolders().firstOrNull { it.name == fb.name }
        if (existing != null) {
            into[fb.name] = existing.folderId
            return
        }
        val parent = fb.parentName?.let { into[it] }
        val created = folderRepository.createFolder(
            Folder(
                folderId = 0L,
                name = fb.name,
                parentFolderId = parent,
                description = fb.description,
                sortOrder = fb.sortOrder,
                createdAt = now,
                updatedAt = now,
            ),
        )
        into[fb.name] = created.folderId
    }
}

private fun Video.withBackupMetadata(
    s: StreamerCatalogItem,
    categoryIds: Map<String, Long>,
    folderIds: Map<String, Long?>,
    now: Instant,
): Video = copy(
    title = s.title.ifBlank { title },
    description = s.description,
    durationMs = s.durationMs ?: durationMs,
    fileSizeBytes = s.fileSizeBytes ?: fileSizeBytes,
    mimeType = s.mimeType ?: mimeType,
    width = s.width ?: width,
    height = s.height ?: height,
    year = s.metadata["year"]?.toIntOrNull() ?: year,
    language = s.metadata["language"]?.takeIf { it.isNotBlank() } ?: language,
    rating = s.metadata["rating"]?.toFloatOrNull() ?: rating,
    categoryId = s.category?.let { categoryIds[it] } ?: categoryId,
    folderId = s.folder?.let { folderIds[it] } ?: folderId,
    tags = s.tags.ifEmpty { tags },
    updatedAt = now,
)
