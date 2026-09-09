package com.mastercontrol.app.core.database.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.mastercontrol.app.core.database.dao.ActivityDao
import com.mastercontrol.app.core.database.dao.CategoryDao
import com.mastercontrol.app.core.database.dao.ChannelDao
import com.mastercontrol.app.core.database.dao.FolderDao
import com.mastercontrol.app.core.database.dao.UploadTaskDao
import com.mastercontrol.app.core.database.dao.VideoDao
import com.mastercontrol.app.core.database.dao.VideoIdAllocatorDao
import com.mastercontrol.app.core.database.entity.ActivityEntity
import com.mastercontrol.app.core.database.entity.CategoryEntity
import com.mastercontrol.app.core.database.entity.ChannelEntity
import com.mastercontrol.app.core.database.entity.FolderEntity
import com.mastercontrol.app.core.database.entity.TelegramMappingEntity
import com.mastercontrol.app.core.database.entity.UploadTaskEntity
import com.mastercontrol.app.core.database.entity.VideoEntity
import com.mastercontrol.app.core.database.entity.VideoIdCounterEntity
import com.mastercontrol.app.core.database.entity.VideoTagEntity

/**
 * Local catalog database. Schema is versioned and must NEVER be destroyed on
 * upgrade: every schema change appends a [Migration] to [MIGRATIONS].
 */
@Database(
    entities = [
        VideoEntity::class,
        TelegramMappingEntity::class,
        VideoTagEntity::class,
        CategoryEntity::class,
        FolderEntity::class,
        ChannelEntity::class,
        UploadTaskEntity::class,
        ActivityEntity::class,
        VideoIdCounterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MasterControlDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun videoIdAllocatorDao(): VideoIdAllocatorDao
    abstract fun categoryDao(): CategoryDao
    abstract fun folderDao(): FolderDao
    abstract fun channelDao(): ChannelDao
    abstract fun uploadTaskDao(): UploadTaskDao
    abstract fun activityDao(): ActivityDao

    companion object {

        const val DATABASE_NAME = "master_control.db"

        /**
         * Explicit migrations in ascending order. Room's
         * `.fallbackToDestructiveMigration()` is intentionally never used.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // Example future migration (kept commented until schema v2 exists):
            // object : Migration(1, 2) {
            //     override fun migrate(db: SupportSQLiteDatabase) {
            //         db.execSQL("ALTER TABLE videos ADD COLUMN example TEXT")
            //     }
            // },
        )

        fun build(context: Context, name: String = DATABASE_NAME): MasterControlDatabase =
            Room.databaseBuilder(context.applicationContext, MasterControlDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/** Guard: verifies migration chain is contiguous from version 1 upward. */
object MigrationChain {
    fun isContiguous(migrations: Array<Migration>): Boolean {
        if (migrations.isEmpty()) return true
        val sorted = migrations.sortedBy { it.startVersion }
        var expected = 1
        for (m in sorted) {
            if (m.startVersion != expected) return false
            if (m.endVersion != m.startVersion + 1) return false
            expected = m.endVersion
        }
        return true
    }

    /** Highest schema version reachable through the migration chain. */
    fun currentVersion(migrations: Array<Migration>): Int =
        migrations.maxOfOrNull { it.endVersion } ?: 1
}
