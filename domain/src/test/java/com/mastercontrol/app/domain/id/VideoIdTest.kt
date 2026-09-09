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
        var previous = VideoId.format(-1) // null sentinel path
        previous = VideoId.nextAfter(null)
        assertEquals("VID-000000", previous)
        for (i in 1..1000) {
            val next = VideoId.nextAfter(previous)
            assertTrue("must keep increasing: $previous -> $next", VideoId.parseSequence(next)!! > VideoId.parseSequence(previous)!!)
            previous = next
        }
        assertNull(VideoId.parseSequence("garbage"))
    }
}
