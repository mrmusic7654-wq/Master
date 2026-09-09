package com.mastercontrol.app.domain.repository

import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import kotlinx.coroutines.flow.Flow

/** Local activity log. */
interface ActivityRepository {

    fun observeRecent(limit: Int = 200): Flow<List<ActivityLogEntry>>

    fun observeByType(type: ActivityType?): Flow<List<ActivityLogEntry>>

    /** Snapshot of the most recent rows (used outside collectors). */
    suspend fun getRecentSnapshot(limit: Int = 50): List<ActivityLogEntry>

    suspend fun add(entry: ActivityLogEntry): Long

    suspend fun count(): Long

    suspend fun clear()
}
