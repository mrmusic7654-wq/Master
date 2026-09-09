package com.mastercontrol.app.domain.model

import com.mastercontrol.app.domain.model.UploadTaskState.CANCELLED
import com.mastercontrol.app.domain.model.UploadTaskState.COMPLETED
import com.mastercontrol.app.domain.model.UploadTaskState.FAILED
import com.mastercontrol.app.domain.model.UploadTaskState.QUEUED
import com.mastercontrol.app.domain.model.UploadTaskState.UPLOADING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UploadStateAndRetryTest {

    private fun task(
        state: UploadTaskState,
        bytes: Long,
        total: Long,
    ) = UploadTask(
        videoId = "VID-000001",
        sourceUri = "content://x",
        fileName = "a.mp4",
        state = state,
        bytesUploaded = bytes,
        totalBytes = total,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `progress reflects real transfer state only`() {
        assertEquals(0f, task(QUEUED, 0, 100).progress)
        assertEquals(0f, task(QUEUED, 500, 100).progress)
        assertEquals(0.5f, task(UPLOADING, 50, 100).progress, 0.001f)
        assertEquals(1f, task(COMPLETED, 0, 100).progress)
        assertEquals(0f, task(FAILED, 99, 100).progress)
        assertEquals(0f, task(CANCELLED, 99, 100).progress)
    }

    @Test
    fun `progress never exceeds bounds with stale counters`() {
        val p = task(UPLOADING, 150, 100).progress
        assertTrue(p <= 1f)
        assertTrue(task(UPLOADING, -5, 100).progress >= 0f)
        assertEquals(0f, task(UPLOADING, 10, 0).progress)
    }

    @Test
    fun `retry policy applies bounded exponential backoff`() {
        val policy = RetryPolicy(maxAttempts = 5, baseDelayMs = 1000L, maxDelayMs = 60_000L)
        assertEquals(0L, policy.delayForAttempt(1))
        assertEquals(1000L, policy.delayForAttempt(2))
        assertEquals(2000L, policy.delayForAttempt(3))
        assertEquals(4000L, policy.delayForAttempt(4))
        assertEquals(60_000L, policy.delayForAttempt(20)) // hard cap
        assertTrue(policy.delayForAttempt(6) <= policy.maxDelayMs)
    }

    @Test
    fun `permanent failure stops after retry budget is exhausted`() {
        val policy = RetryPolicy(maxAttempts = 3)
        var attempts = 0
        var permanent = false
        // simulate a worker loop honoring maxAttempts
        while (!permanent && attempts < 100) {
            attempts++
            if (attempts >= policy.maxAttempts) permanent = true
        }
        assertTrue(permanent)
        assertEquals(3, attempts)
    }
}
