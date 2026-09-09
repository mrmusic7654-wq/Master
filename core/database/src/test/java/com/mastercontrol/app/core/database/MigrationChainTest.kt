package com.mastercontrol.app.core.database

import androidx.room.migration.Migration
import com.mastercontrol.app.core.database.database.MigrationChain
import com.mastercontrol.app.core.database.database.MasterControlDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationChainTest {

    @Test
    fun `empty chain means schema version 1 with no destructive fallback configured`() {
        assertTrue(MigrationChain.isContiguous(MasterControlDatabase.MIGRATIONS))
        assertEquals(1, MigrationChain.currentVersion(MasterControlDatabase.MIGRATIONS))
        assertTrue(MasterControlDatabase.MIGRATIONS.isEmpty())
    }

    @Test
    fun `chain must be contiguous from version 1`() {
        assertTrue(
            MigrationChain.isContiguous(
                arrayOf(
                    object : Migration(1, 2) {
                        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    },
                    object : Migration(2, 3) {
                        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    },
                ),
            ),
        )
        assertEquals(3, MigrationChain.currentVersion(
            arrayOf(
                object : Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                },
                object : Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                },
            ),
        ))
        // Skipped version breaks the chain.
        assertFalse(
            MigrationChain.isContiguous(
                arrayOf(
                    object : Migration(1, 3) {
                        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    },
                ),
            ),
        )
    }
}
