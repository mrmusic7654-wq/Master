package com.mastercontrol.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mastercontrol.app.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChannelDao {

    @Query("SELECT * FROM channels ORDER BY isDefault DESC, title COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isDefault = 1 LIMIT 1")
    abstract fun observeDefault(): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE channelId = :channelId LIMIT 1")
    abstract suspend fun get(channelId: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE isDefault = 1 LIMIT 1")
    abstract suspend fun getDefault(): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(channel: ChannelEntity)

    @Update
    abstract suspend fun update(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE channelId = :channelId")
    abstract suspend fun delete(channelId: Long)

    @Query("UPDATE channels SET isDefault = 0")
    abstract suspend fun clearDefaultFlag()

    @Query("UPDATE channels SET isDefault = 1, updatedAtEpochMs = :updatedAtEpochMs WHERE channelId = :channelId")
    abstract suspend fun markDefault(channelId: Long, updatedAtEpochMs: Long)

    @Query("SELECT COUNT(*) FROM channels")
    abstract suspend fun count(): Int

    /** Selects one default channel atomically. */
    @Transaction
    open suspend fun setDefault(channelId: Long, updatedAtEpochMs: Long) {
        clearDefaultFlag()
        markDefault(channelId, updatedAtEpochMs)
    }
}
