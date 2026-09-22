package com.mastercontrol.app.core.common.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRateEstimatorTest {

    @Test
    fun `rate is derived from observed counters only`() {
        val estimator = TransferRateEstimator(windowMs = 5_000)
        // No samples yet: honestly unknown.
        assertNull(estimator.bytesPerSecond(nowMs = 1_000))

        estimator.record(bytesUploaded = 0, atMs = 0)
        estimator.record(bytesUploaded = 1_048_576, atMs = 1_000) // 1 MiB in 1 s
        val rate = estimator.bytesPerSecond(nowMs = 1_000)
        assertTrue("rate should be ~1 MiB/s but was $rate", rate != null && rate in 1_000_000.0..1_100_000.0)
    }

    @Test
    fun `rate is unknown when nothing moved yet`() {
        val estimator = TransferRateEstimator()
        estimator.record(0, atMs = 0)
        assertNull("a single sample cannot produce a rate", estimator.bytesPerSecond(nowMs = 10))
    }

    @Test
    fun `stalled transfer reports zero rather than a stale rate`() {
        val estimator = TransferRateEstimator(windowMs = 5_000)
        estimator.record(1_000_000, atMs = 0)
        estimator.record(2_000_000, atMs = 1_000)
        estimator.record(2_000_000, atMs = 3_000)
        assertEquals(0.0, estimator.bytesPerSecond(nowMs = 3_000)!!, 0.001)
    }

    @Test
    fun `eta uses the real rate and the real remaining bytes`() {
        val estimator = TransferRateEstimator(windowMs = 10_000)
        estimator.record(0, atMs = 0)
        estimator.record(1_000_000, atMs = 1_000) // 1 MB/s
        val eta = estimator.remainingSeconds(bytesUploaded = 1_000_000, totalBytes = 41_000_000, nowMs = 1_000)
        assertEquals(40L, eta)
        // Unknown total size: no fabricated ETA.
        assertNull(estimator.remainingSeconds(1_000_000, null, nowMs = 1_000))
        // Already finished.
        assertEquals(0L, estimator.remainingSeconds(41_000_000, 41_000_000, nowMs = 1_000))
    }

    @Test
    fun `a restarted transfer drops the window instead of reporting a negative rate`() {
        val estimator = TransferRateEstimator(windowMs = 5_000)
        estimator.record(5_000_000, atMs = 0)
        estimator.record(9_000_000, atMs = 1_000)
        // Process death / retry restarts the upload from zero: the window is
        // dropped and only the restart point remains as the new baseline, so no
        // negative rate can ever be reported.
        estimator.record(0, atMs = 2_000)
        assertEquals(1, estimator.sampleCount)
        assertNull(estimator.bytesPerSecond(nowMs = 2_000))
        estimator.record(1_000_000, atMs = 3_000)
        estimator.record(2_000_000, atMs = 4_000)
        val rate = estimator.bytesPerSecond(nowMs = 4_000)
        assertTrue("expected a positive rate after restart but was $rate", rate != null && rate > 0.0)
    }

    @Test
    fun `samples outside the window are evicted`() {
        val estimator = TransferRateEstimator(windowMs = 2_000)
        estimator.record(0, atMs = 0)
        estimator.record(1_000_000, atMs = 1_000)
        estimator.record(2_000_000, atMs = 2_000)
        estimator.record(6_000_000, atMs = 6_000)
        // Only the recent window remains, so the rate reflects the latest leg.
        val rate = estimator.bytesPerSecond(nowMs = 6_000)
        assertTrue(rate != null && rate > 0.0)
        assertTrue(estimator.sampleCount <= 4)
    }

    @Test
    fun `reset clears the estimator for the next task`() {
        val estimator = TransferRateEstimator()
        estimator.record(1_000, atMs = 0)
        estimator.record(2_000, atMs = 500)
        estimator.reset()
        assertEquals(0, estimator.sampleCount)
        assertNull(estimator.bytesPerSecond(nowMs = 600))
    }
}
