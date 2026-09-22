package com.mastercontrol.app.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoIdTest {

    @Test
    fun `formats zero-padded permanent ids`() {
        assertEquals("VID-000000", VideoId.format(0))
        assertEquals("VID-000001", VideoId.format(1))
        assertEquals("VID-000101", VideoId.format(101))
        assertEquals("VID-1000001", VideoId.format(1_000_001))
    }

    @Test
    fun `round trips sequence numbers`() {
        assertEquals(0L, VideoId.parseSequence("VID-000000"))
        assertEquals(8472L, VideoId.parseSequence("VID-008472"))
        assertEquals(1_000_001L, VideoId.parseSequence("VID-1000001"))
    }

    @Test
    fun `accepts only canonical ids`() {
        assertTrue(VideoId.isValid("VID-000001"))
        assertFalse(VideoId.isValid("vid-000001"))
        assertFalse(VideoId.isValid("VID-1"))
        assertFalse(VideoId.isValid("VID-00000A"))
        assertFalse(VideoId.isValid("000001"))
        assertFalse(VideoId.isValid(""))
    }

    @Test
    fun `ids are monotonically increasing and never reused`() {
        // The allocator starts from an empty catalog: the first permanent ID is VID-000000.
        var previous = VideoId.nextAfter(null)
        assertEquals("VID-000000", previous)
        for (i in 1..1000) {
            val next = VideoId.nextAfter(previous)
            assertTrue("must keep increasing: $previous -> $next", VideoId.parseSequence(next)!! > VideoId.parseSequence(previous)!!)
            previous = next
        }
        assertNull(VideoId.parseSequence("garbage"))
        assertNull(VideoId.parseSequence(null))
        // format() must reject negative sequences rather than emit a malformed ID.
        try {
            VideoId.format(-1)
            org.junit.Assert.fail("format(-1) must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("sequence"))
        }
    }
}
