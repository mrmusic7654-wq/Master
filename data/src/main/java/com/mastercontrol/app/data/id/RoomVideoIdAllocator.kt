package com.mastercontrol.app.data.id

import androidx.room.withTransaction
import com.mastercontrol.app.core.database.database.MasterControlDatabase
import com.mastercontrol.app.domain.id.VideoId
import com.mastercontrol.app.domain.id.VideoIdAllocator
import javax.inject.Inject
import javax.inject.Singleton

/** Transactional, collision-safe allocator backed by the Room counter table. */
@Singleton
class RoomVideoIdAllocator @Inject constructor(
    private val db: MasterControlDatabase,
) : VideoIdAllocator {

    override suspend fun allocate(): String =
        db.withTransaction {
            val sequence = db.videoIdAllocatorDao().allocate()
            VideoId.format(sequence)
        }
}
