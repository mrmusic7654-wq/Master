package com.mastercontrol.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBackupSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `backup round-trips through JSON`() {
        val backup = CatalogBackup(
            exportedAt = "2026-09-09T00:00:00Z",
            categories = listOf(CategoryBackup(name = "Movies", sortOrder = 1)),
            items = listOf(
                StreamerCatalogItem(
                    videoId = "VID-000001",
                    title = "Test",
                    durationMs = 60_000,
                    telegramChannelId = -1001234567890L,
                    telegramMessageId = 8472L,
                    tags = listOf("1080p", "english"),
                    metadata = mapOf("year" to "2026"),
                ),
            ),
        )
        val text = json.encodeToString(CatalogBackup.serializer(), backup)
        assertTrue(text.contains("\"videoId\":\"VID-000001\""))
        assertTrue(text.contains("\"telegramMessageId\":8472"))

        val decoded = json.decodeFromString(CatalogBackup.serializer(), text)
        assertEquals(1, decoded.items.size)
        assertEquals("VID-000001", decoded.items[0].videoId)
        assertEquals(-1001234567890L, decoded.items[0].telegramChannelId)
        assertEquals(listOf("1080p", "english"), decoded.items[0].tags)
        assertEquals("2026", decoded.items[0].metadata["year"])
    }

    @Test
    fun `streamer catalog items do not carry any secret fields`() {
        val item = StreamerCatalogItem(videoId = "VID-000002", title = "x")
        val encoded = json.encodeToString(StreamerCatalogItem.serializer(), item)
        assertFalse(encoded.contains("hash", ignoreCase = true))
        assertFalse(encoded.contains("session", ignoreCase = true))
        assertFalse(encoded.contains("password", ignoreCase = true))
        assertNull(item.telegramChannelId)
    }

    @Test
    fun `empty catalog export is valid`() {
        val backup = CatalogBackup(exportedAt = "2026-09-09T00:00:00Z")
        val decoded = json.decodeFromString(CatalogBackup.serializer(), json.encodeToString(CatalogBackup.serializer(), backup))
        assertTrue(decoded.items.isEmpty())
        assertEquals(1, decoded.formatVersion)
    }
}
