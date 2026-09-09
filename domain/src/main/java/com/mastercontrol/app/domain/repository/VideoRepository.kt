package com.mastercontrol.app.domain.repository

import com.mastercontrol.app.domain.model.LibraryQuery
import com.mastercontrol.app.domain.model.LibraryStatistics
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import kotlinx.coroutines.flow.Flow

/** Catalog (video / mapping) persistence. Categories and folders have their own repositories. */
interface VideoRepository {

    fun observeAll(query: LibraryQuery = LibraryQuery()): Flow<List<Video>>

    fun observeVideo(videoId: String): Flow<Video?>

    suspend fun getVideo(videoId: String): Video?

    fun observeMapping(videoId: String): Flow<TelegramMapping?>

    suspend fun getMapping(videoId: String): TelegramMapping?

    suspend fun createVideo(video: Video): Video

    suspend fun updateVideo(video: Video): Video

    suspend fun deleteVideoLocally(videoId: String)

    /**
     * Atomic commit of a finished upload:
     * create/activate the [TelegramMapping], mark the video COMPLETE and clear the upload task.
     * If persistence fails after Telegram accepted the message, the receipt is not lost:
     * callers must keep the receipt and reconcile later (see ReconcileUseCase).
     */
    suspend fun commitCompletedUpload(
        videoId: String,
        mapping: TelegramMapping,
        taskId: Long?,
    )

    suspend fun setVideoStatus(videoId: String, status: VideoStatus)

    suspend fun setThumbnail(videoId: String, thumbnailUri: String?, posterUri: String? = null)

    suspend fun attachHash(videoId: String, sha256: String)

    suspend fun setMappingStatus(videoId: String, status: MappingStatus)

    /** Every mapping row (used by the reconciliation engine). */
    suspend fun getAllMappings(): List<TelegramMapping>

    /** All videos (used by export/import and reconciliation). */
    suspend fun getAllVideos(): List<Video>

    // ---- duplicate detection ----------------------------------------------

    suspend fun findDuplicateBySizeName(sizeBytes: Long, fileName: String): Video?

    suspend fun findDuplicateByHash(sha256: String): Video?

    /** Compute dashboard statistics from real rows. */
    suspend fun computeStatistics(): LibraryStatistics

    /** Diagnostics. */
    suspend fun countVideos(): Long

    suspend fun countProblematicMappings(): Long
}
