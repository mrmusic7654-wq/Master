package com.mastercontrol.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mastercontrol.app.core.database.entity.GroupedCount
import com.mastercontrol.app.core.database.entity.GroupedSum
import com.mastercontrol.app.core.database.entity.TelegramMappingEntity
import com.mastercontrol.app.core.database.entity.VideoEntity
import com.mastercontrol.app.core.database.entity.VideoIdCounterEntity
import com.mastercontrol.app.core.database.entity.VideoTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    // ---- videos -----------------------------------------------------------

    @Query(
        """
        SELECT DISTINCT v.* FROM videos v
        LEFT JOIN video_tags vt ON vt.videoId = v.videoId
        LEFT JOIN categories c ON c.categoryId = v.categoryId
        LEFT JOIN folders f ON f.folderId = v.folderId
        WHERE
            (:text = '' OR
                v.videoId LIKE '%' || :text || '%' OR
                lower(v.title) LIKE '%' || lower(:text) || '%' OR
                lower(v.originalFileName) LIKE '%' || lower(:text) || '%' OR
                lower(v.description) LIKE '%' || lower(:text) || '%' OR
                vt.tag LIKE '%' || lower(:text) || '%' OR
                lower(c.name) LIKE '%' || lower(:text) || '%' OR
                lower(f.name) LIKE '%' || lower(:text) || '%')
            AND (:categoryId IS NULL OR v.categoryId = :categoryId)
            AND (:folderId IS NULL OR v.folderId = :folderId)
            AND (:status IS NULL OR v.status = :status)
            AND (:tag IS NULL OR EXISTS (SELECT 1 FROM video_tags ft WHERE ft.videoId = v.videoId AND ft.tag = lower(:tag)))
        GROUP BY v.videoId
        ORDER BY v.id DESC
        """,
    )
    fun observeLibrary(text: String, categoryId: Long?, folderId: Long?, status: String?, tag: String?): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE videoId = :videoId LIMIT 1")
    fun observeVideo(videoId: String): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getVideo(videoId: String): VideoEntity?

    @Query("SELECT * FROM videos")
    suspend fun getAllVideos(): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(video: VideoEntity): Long

    @Update
    suspend fun update(video: VideoEntity)

    @Query("DELETE FROM videos WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("UPDATE videos SET status = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE videoId = :videoId")
    suspend fun setStatus(videoId: String, status: String, updatedAtEpochMs: Long)

    @Query("UPDATE videos SET thumbnailUri = :thumbnailUri, posterUri = :posterUri, updatedAtEpochMs = :updatedAtEpochMs WHERE videoId = :videoId")
    suspend fun setThumbnail(videoId: String, thumbnailUri: String?, posterUri: String?, updatedAtEpochMs: Long)

    @Query("UPDATE videos SET sha256 = :sha256, hashPending = 0, updatedAtEpochMs = :updatedAtEpochMs WHERE videoId = :videoId")
    suspend fun attachHash(videoId: String, sha256: String, updatedAtEpochMs: Long)

    // ---- duplicates / diagnostics ----------------------------------------

    @Query("SELECT * FROM videos WHERE fileSizeBytes = :sizeBytes AND lower(originalFileName) = lower(:fileName) AND originalFileName != '' LIMIT 1")
    suspend fun findBySizeAndName(sizeBytes: Long, fileName: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findByHash(sha256: String): VideoEntity?

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun countVideos(): Long

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM videos")
    suspend fun sumFileSizes(): Long

    @Query("SELECT COUNT(*) FROM videos WHERE thumbnailUri IS NOT NULL AND thumbnailUri != ''")
    suspend fun countVideosWithThumbnail(): Long

    @Query("SELECT COUNT(*) FROM videos WHERE status = :status")
    suspend fun countVideosByStatus(status: String): Long

    @Query("SELECT COUNT(*) FROM videos WHERE categoryId IS NOT NULL")
    suspend fun countVideosWithCategory(): Long

    @Query("SELECT COUNT(*) FROM videos WHERE folderId IS NOT NULL")
    suspend fun countVideosWithFolder(): Long

    /**
     * Moves every video of [fromFolderId] to [targetFolderId] (null = unfiled).
     *
     * Used by folder deletion so the operator's choice is actually applied
     * inside the same transaction that removes the folder.
     */
    @Query("UPDATE videos SET folderId = :targetFolderId, updatedAtEpochMs = :updatedAtEpochMs WHERE folderId = :fromFolderId")
    suspend fun reassignFolder(fromFolderId: Long, targetFolderId: Long?, updatedAtEpochMs: Long)

    @Query("SELECT channelId AS ownerId, COUNT(*) AS count FROM telegram_mappings WHERE mappingStatus = 'ACTIVE' GROUP BY channelId")
    suspend fun countActiveMappingsByChannel(): List<GroupedCount>

    @Query("SELECT channelId AS ownerId, COALESCE(SUM(fileSizeBytes), 0) AS total FROM telegram_mappings WHERE mappingStatus = 'ACTIVE' GROUP BY channelId")
    suspend fun sumActiveMappingBytesByChannel(): List<GroupedSum>

    @Query("SELECT categoryId AS ownerId, COUNT(*) AS count FROM videos WHERE categoryId IS NOT NULL GROUP BY categoryId")
    suspend fun countByCategory(): List<GroupedCount>

    @Query("SELECT folderId AS ownerId, COUNT(*) AS count FROM videos WHERE folderId IS NOT NULL GROUP BY folderId")
    suspend fun countByFolder(): List<GroupedCount>

    // ---- tags -------------------------------------------------------------

    @Query("SELECT * FROM video_tags")
    fun observeAllTags(): Flow<List<VideoTagEntity>>

    @Query("SELECT tag FROM video_tags WHERE videoId = :videoId ORDER BY tag")
    suspend fun tagsForVideo(videoId: String): List<String>

    @Query("SELECT DISTINCT tag FROM video_tags ORDER BY tag")
    suspend fun distinctTags(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<VideoTagEntity>)

    @Query("DELETE FROM video_tags WHERE videoId = :videoId")
    suspend fun deleteTags(videoId: String)

    // ---- mappings ---------------------------------------------------------

    @Query("SELECT * FROM telegram_mappings WHERE videoId = :videoId LIMIT 1")
    fun observeMapping(videoId: String): Flow<TelegramMappingEntity?>

    @Query("SELECT * FROM telegram_mappings WHERE videoId = :videoId LIMIT 1")
    suspend fun getMapping(videoId: String): TelegramMappingEntity?

    @Query("SELECT * FROM telegram_mappings")
    suspend fun getAllMappings(): List<TelegramMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapping(mapping: TelegramMappingEntity)

    @Query("UPDATE telegram_mappings SET mappingStatus = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE videoId = :videoId")
    suspend fun setMappingStatus(videoId: String, status: String, updatedAtEpochMs: Long)

    @Query("DELETE FROM telegram_mappings WHERE videoId = :videoId")
    suspend fun deleteMapping(videoId: String)

    @Query("SELECT COUNT(*) FROM telegram_mappings WHERE mappingStatus NOT IN ('NONE', 'ACTIVE')")
    suspend fun countProblematicMappings(): Long

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM telegram_mappings WHERE mappingStatus = 'ACTIVE'")
    suspend fun sumActiveMappingBytes(): Long

    @Query("SELECT COUNT(*) FROM telegram_mappings WHERE mappingStatus = 'ACTIVE'")
    suspend fun countActiveMappings(): Long

    // ---- video id counter --------------------------------------------------

    @Query("SELECT * FROM video_id_counter WHERE id = 1")
    suspend fun getCounter(): VideoIdCounterEntity?

    @Query("INSERT OR REPLACE INTO video_id_counter(id, nextSequence) VALUES (1, :next)")
    suspend fun setCounter(next: Long)
}

/**
 * Transactional Video ID allocation: increment and read the counter inside one
 * Room transaction so concurrent importers can never receive the same ID.
 */
@Dao
abstract class VideoIdAllocatorDao {

    @Query("SELECT * FROM video_id_counter WHERE id = 1")
    abstract suspend fun current(): VideoIdCounterEntity?

    @Query("UPDATE video_id_counter SET nextSequence = :next WHERE id = 1")
    abstract suspend fun advance(next: Long)

    @Query("INSERT INTO video_id_counter(id, nextSequence) VALUES (1, :next)")
    abstract suspend fun seed(next: Long)

    @Transaction
    open suspend fun allocate(): Long {
        val currentSeq = current()?.nextSequence ?: 0L
        val allocated = currentSeq + 1L
        if (current() == null) seed(allocated) else advance(allocated)
        return allocated
    }
}
