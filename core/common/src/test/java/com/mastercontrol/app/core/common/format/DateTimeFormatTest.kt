package com.mastercontrol.app.core.common.format

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeFormatTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun `unknown timestamps render as an em dash, never a fake date`() {
        assertEquals("—", DateTimeFormat.date(null))
        assertEquals("—", DateTimeFormat.dateTime(null))
        assertEquals("—", DateTimeFormat.time(null))
        assertEquals("—", DateTimeFormat.instant(null))
        assertEquals("—", DateTimeFormat.relative(null))
    }

    @Test
    fun `absolute timestamps render in the requested zone`() {
        // 2026-01-02T03:04:05Z — the exact layout is locale-dependent, the date is not.
        val epochMs = 1_767_323_045_000L
        val rendered = DateTimeFormat.dateTime(epochMs, utc)
        assertTrue("expected the year in '$rendered'", rendered.contains("2026"))
        assertNotEquals("—", rendered)
        assertTrue("date-only rendering must not be empty", DateTimeFormat.date(epochMs, utc).isNotBlank())
        assertTrue("time-only rendering must not be empty", DateTimeFormat.time(epochMs, utc).isNotBlank())
        assertEquals(DateTimeFormat.instant(java.time.Instant.ofEpochMilli(epochMs), utc), rendered)
    }

    @Test
    fun `relative ages describe the past only`() {
        val now = 1_767_323_045_000L
        assertEquals("just now", DateTimeFormat.relative(now - 5_000, now, utc))
        assertEquals("1 min ago", DateTimeFormat.relative(now - 70_000, now, utc))
        assertEquals("5 min ago", DateTimeFormat.relative(now - 5 * 60_000, now, utc))
        assertEquals("3 h ago", DateTimeFormat.relative(now - 3 * 3_600_000L, now, utc))
        assertEquals("yesterday", DateTimeFormat.relative(now - 40 * 3_600_000L, now, utc))
        assertEquals("5 days ago", DateTimeFormat.relative(now - 5L * 24 * 3_600_000, now, utc))
        // A future timestamp is rendered absolutely rather than as "ago".
        assertTrue(DateTimeFormat.relative(now + 3_600_000L, now, utc).contains("2026"))
        // Older than ~30 days falls back to the absolute date.
        assertTrue(DateTimeFormat.relative(now - 90L * 24 * 3_600_000, now, utc).contains("2025"))
    }
}
