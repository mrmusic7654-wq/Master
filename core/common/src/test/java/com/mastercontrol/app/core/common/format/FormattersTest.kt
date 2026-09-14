package com.mastercontrol.app.core.common.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `bytes renders binary units and unknown values`() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.0 KB", Formatters.bytes(1024))
        assertEquals("1.5 MB", Formatters.bytes(1_572_864))
        assertEquals("2.0 GB", Formatters.bytes(2L * 1024 * 1024 * 1024))
        assertEquals("—", Formatters.bytes(null))
    }

    @Test
    fun `rate renders real transfer speeds only`() {
        assertEquals("—", Formatters.rate(null))
        assertEquals("—", Formatters.rate(0.0))
        assertEquals("—", Formatters.rate(-5.0))
        assertEquals("12.4 MB/s", Formatters.rate(12.4 * 1024 * 1024))
        assertEquals("800 B/s", Formatters.rate(800.0))
    }

    @Test
    fun `duration switches to hours past one hour`() {
        assertEquals("00:00", Formatters.duration(0))
        assertEquals("04:07", Formatters.duration(247_000))
        assertEquals("59:59", Formatters.duration(3_599_000))
        assertEquals("1:00:00", Formatters.duration(3_600_000))
        assertEquals("2:04:07", Formatters.duration(((2 * 3600) + (4 * 60) + 7) * 1000L))
        assertEquals("—", Formatters.duration(null))
        assertEquals("—", Formatters.duration(-1))
    }

    @Test
    fun `eta renders countdown from remaining seconds`() {
        assertEquals("00:41", Formatters.eta(41))
        assertEquals("1:00:00", Formatters.eta(3600))
        assertEquals("—", Formatters.eta(null))
    }

    @Test
    fun `percent is clamped and rounded`() {
        assertEquals("0%", Formatters.percent(-1f))
        assertEquals("82%", Formatters.percent(0.82f))
        assertEquals("100%", Formatters.percent(1f))
        assertEquals("100%", Formatters.percent(4f))
        assertEquals("0%", Formatters.percent(Float.NaN))
    }

    @Test
    fun `resolution and frame rate describe real media only`() {
        assertEquals("1920 × 1080", Formatters.resolution(1920, 1080))
        assertEquals("—", Formatters.resolution(null, 1080))
        assertEquals("—", Formatters.resolution(0, 0))
        assertEquals("1080p", Formatters.resolutionLabel(1920, 1080))
        assertEquals("—", Formatters.resolutionLabel(null, null))
        assertEquals("23.98 fps", Formatters.frameRate(23.976))
        assertEquals("—", Formatters.frameRate(null))
        assertEquals("—", Formatters.frameRate(0.0))
    }

    @Test
    fun `mime type renders a readable container label`() {
        assertEquals("MP4", Formatters.formatFromMime("video/mp4"))
        assertEquals("MP4", Formatters.formatFromMime("VIDEO/MP4"))
        assertEquals("MATROSKA", Formatters.formatFromMime("video/x-matroska"))
        assertEquals("3GPP", Formatters.formatFromMime("video/3gpp"))
        assertEquals("—", Formatters.formatFromMime(null))
        assertEquals("—", Formatters.formatFromMime("  "))
    }

    @Test
    fun `compact counts stay readable on dashboard tiles`() {
        assertEquals("0", Formatters.compactCount(0))
        assertEquals("999", Formatters.compactCount(999))
        assertEquals("1.5k", Formatters.compactCount(1_500))
        assertEquals("12k", Formatters.compactCount(12_000))
        assertEquals("3.4M", Formatters.compactCount(3_400_000))
        assertEquals("2B", Formatters.compactCount(2_000_000_000))
        assertTrue(Formatters.compactCount(Long.MAX_VALUE).endsWith("B"))
    }
}
