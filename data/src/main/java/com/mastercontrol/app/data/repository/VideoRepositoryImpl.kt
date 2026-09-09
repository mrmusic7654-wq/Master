package com.mastercontrol.app.data.repository

import androidx.room.withTransaction
import com.mastercontrol.app.core.database.database.MasterControlDatabase
import com.mastercontrol.app.core.database.entity.VideoTagEntity
import com.mastercontrol.app.data.local.toDomain
import com.mastercontrol.app.data.local.toEntity
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.LibraryQuery
import com.mastercontrol.app.domain.model.LibrarySort
import com.mastercontrol.app.domain.model.LibraryStatistics
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val db: MasterControlDatabase,
) : VideoRepository {

    private val videoDao get() = db.videoDao()
    private val uploadTaskDao get() = db.uploadTaskDao()
    private val allocatorDao get() = db.videoIdAllocatorDao()

    override fun observeAll(query: LibraryQuery): Flow<List<Video>> {
        val videosFlow = videoDao().observeLibrary(
            text = query.text.trim(),
            categoryId = query.categoryId,
            folderId = query.folderId,
            status = query.status?.name,
            tag = query.tag?.lowercase(),
        ).map { list -> list.sortedWith(query.sort.comparator).map { it.toDomain(emptyList()) } }
        val tagsFlow = videoDao().observeAllTags()
        return combine(videosFlow, tagsFlow) { videos, tags ->
            videos.map { v ->
                if (v.tags.isEmpty()) {
                    v.copy(tags = tags.filter { it.videoId == v.videoId }.map { it.tag })
                } else v
            }
        }
    }

    override fun observeVideo(videoId: String): Flow<Video?> {
        val videoFlow = videoDao().observeVideo(videoId).map { it?.toDomain(emptyList()) }
        val tagsFlow = videoDao().observeAllTags()
        return combine(videoFlow, tagsFlow) { video, tags ->
            video?.copy(tags = tags.filter { it.videoId == videoId }.map { it.tag })
        }
    }

    override suspend fun getVideo(videoId: String): Video? =
        videoDao().getVideo(videoId)?.let { it.toDomain(videoDao().tagsForVideo(videoId)) }

    override fun observeMapping(videoId: String): Flow<TelegramMapping?> =
        videoDao().observeMapping(videoId).map { it?.toDomain() }

    override suspend fun getMapping(videoId: String): TelegramMapping? =
        videoDao().getMapping(videoId)?.toDomain()

    override suspend fun createVideo(video: Video): Video {
        return db.withTransaction {
            val allocated = video.videoId.ifBlank { allocatorDao().allocate() }
            val entity = video.copy(videoId = allocated, status = VideoStatus.IMPORTING).toEntity()
            try {
                videoDao().insert(entity)
            } catch (t: Throwable) {
                throw AppError.DatabaseError("Could not insert video $allocated.", t)
            }
            insertTags(allocated, video.tags)
            video.copy(videoId = allocated)
        }
    }

    override suspend fun updateVideo(video: Video): Video {
        db.withTransaction {
            videoDao().update(video.toEntity())
            videoDao().deleteTags(video.videoId)
            insertTags(video.videoId, video.tags)
        }
        return video
    }

    override suspend fun deleteVideoLocally(videoId: String) {
        db.withTransaction { videoDao().deleteByVideoId(videoId) }
    }

    override suspend fun commitCompletedUpload(videoId: String, mapping: TelegramMapping, taskId: Long?) {
        db.withTransaction {
            videoDao().upsertMapping(mapping.toEntity())
            videoDao().setStatus(videoId, VideoStatus.COMPLETE.name, mapping.updatedAt.toEpochMilli())
            if (taskId != null) {
                val task = uploadTaskDao().get(taskId)
                if (task != null) {
                    uploadTaskDao().update(
                        task.copy(
                            state = UploadTaskState.COMPLETED.name,
                            bytesUploaded = mapping.fileSizeBytes ?: task.bytesUploaded,
                            updatedAtEpochMs = mapping.updatedAt.toEpochMilli(),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun setVideoStatus(videoId: String, status: VideoStatus) {
        videoDao().setStatus(videoId, status.name, Instant.now().toEpochMilli())
    }

    override suspend fun setThumbnail(videoId: String, thumbnailUri: String?, posterUri: String?) {
        videoDao().setThumbnail(videoId, thumbnailUri, posterUri, Instant.now().toEpochMilli())
    }

    override suspend fun attachHash(videoId: String, sha256: String) {
        videoDao().attachHash(videoId, sha256, Instant.now().toEpochMilli())
    }

    override suspend fun setMappingStatus(videoId: String, status: MappingStatus) {
        videoDao().setMappingStatus(videoId, status.name, Instant.now().toEpochMilli())
    }

    override suspend fun deleteRemoteMappingMarker(videoId: String) {
        videoDao().deleteMapping(videoId)
    }

    override suspend fun findDuplicateBySizeName(sizeBytes: Long, fileName: String): Video? =
        videoDao().findBySizeAndName(sizeBytes, fileName)?.let { it.toDomain(videoDao().tagsForVideo(it.videoId)) }

    override suspend fun findDuplicateByHash(sha256: String): Video? =
        videoDao().findByHash(sha256)?.let { it.toDomain(videoDao().tagsForVideo(it.videoId)) }

    override suspend fun getAllMappings(): List<TelegramMapping> =
        videoDao().getAllMappings().map { it.toDomain() }

    override suspend fun getAllVideos(): List<Video> =
        videoDao().getAllVideos().map { it.toDomain(videoDao().tagsForVideo(it.videoId)) }

    override suspend fun computeStatistics(): LibraryStatistics {
        val allVideos = videoDao().getAllVideos()
        val allMappings = videoDao().getAllMappings()
        val activeMappings = allMappings.filter { it.mappingStatus == MappingStatus.ACTIVE.name }
        return LibraryStatistics(
            totalVideos = allVideos.size,
            totalLocalMediaBytes = allVideos.sumOf { it.fileSizeBytes ?: 0L },
            totalTelegramReferencedBytes = activeMappings.sumOf { it.fileSizeBytes ?: 0L },
            completedUploads = videoDao().countVideosByStatus(VideoStatus.COMPLETE.name).toInt(),
            pendingUploads = uploadTaskDao().countByState(UploadTaskState.QUEUED.name) +
                uploadTaskDao().countByState(UploadTaskState.RETRYING.name),
            failedUploads = uploadTaskDao().countByState(UploadTaskState.FAILED.name),
            uploadsInProgress = uploadTaskDao().countByState(UploadTaskState.UPLOADING.name) +
                uploadTaskDao().countByState(UploadTaskState.PREPARING.name) +
                uploadTaskDao().countByState(UploadTaskState.VERIFYING.name),
            totalTags = videoDao().distinctTags().size,
            totalLocalThumbnails = allVideos.count { !it.thumbnailUri.isNullOrBlank() },
            pendingUploadBytes = uploadTaskDao().sumPendingBytes(),
            completedMediaCount = activeMappings.size,
            remoteDeletedCount = allMappings.count { it.mappingStatus == MappingStatus.REMOTE_DELETED.name },
        )
    }

    override suspend fun countVideos(): Long = videoDao().countVideos()

    override suspend fun countProblematicMappings(): Long = videoDao().countProblematicMappings()

    private suspend fun insertTags(videoId: String, tags: List<String>) {
        videoDao().insertTags(tags.distinct().map { VideoTagEntity(videoId, it.trim().lowercase()) }.filter { it.tag.isNotBlank() })
    }
}

private val LibrarySort.comparator: Comparator<Video>
    get() = when (this) {
        LibrarySort.DATE_ADDED_DESC -> compareByDescending<Video> { it.createdAt }
        LibrarySort.DATE_ADDED_ASC -> compareBy<Video> { it.createdAt }
        LibrarySort.TITLE_ASC -> compareBy<Video> { it.title.lowercase() }
        LibrarySort.TITLE_DESC -> compareByDescending<Video> { it.title.lowercase() }
        LibrarySort.DURATION_DESC -> compareByDescending<Video> { it.durationMs ?: 0L }
        LibrarySort.SIZE_DESC -> compareByDescending<Video> { it.fileSizeBytes ?: 0L }
        LibrarySort.YEAR_DESC -> compareByDescending<Video> { it.year ?: 0 }
        LibrarySort.STATUS_ASC -> compareBy<Video> { it.status.name }
    }
