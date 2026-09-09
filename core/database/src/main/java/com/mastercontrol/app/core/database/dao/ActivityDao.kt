package com.mastercontrol.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mastercontrol.app.core.database.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activity_log ORDER BY activityId DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity_log WHERE (:type IS NULL OR type = :type) ORDER BY activityId DESC LIMIT :limit")
    fun observeFiltered(type: String?, limit: Int): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity_log ORDER BY activityId DESC LIMIT :limit")
    suspend fun recentSnapshot(limit: Int): List<ActivityEntity>

    @Insert
    suspend fun insert(entry: ActivityEntity): Long

    @Query("SELECT COUNT(*) FROM activity_log")
    suspend fun count(): Long

    @Query("DELETE FROM activity_log")
    suspend fun clear()

    /** Removes the oldest [count] rows (log housekeeping). */
    @Query("DELETE FROM activity_log WHERE activityId IN (SELECT activityId FROM activity_log ORDER BY activityId ASC LIMIT :count)")
    suspend fun trimOldest(count: Int)
}
