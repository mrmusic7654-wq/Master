package com.mastercontrol.app.core.common.transfer

/**
 * Computes a transfer rate and ETA from *observed* byte counters.
 *
 * Master Control never fabricates progress: the upload worker persists
 * `bytesUploaded` from real TDLib `updateFile` events, and this estimator turns
 * that series of counters into a smoothed rate over a sliding time window.
 *
 * The estimator is intentionally free of Android and coroutine types so it can
 * be unit tested and reused by both the upload UI and diagnostics.
 *
 * Not thread-safe: confine it to one collector (a ViewModel on the main
 * dispatcher, or a single worker coroutine).
 */
class TransferRateEstimator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxSamples: Int = 64,
) {
    private data class Sample(val atMs: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()

    /** Highest counter seen so far; used to detect restarts of the same task. */
    private var peakBytes: Long = 0L

    val sampleCount: Int get() = samples.size

    /**
     * Records an observed counter value.
     *
     * A counter that goes *backwards* means the transfer restarted from scratch
     * (retry, process death recovery, TDLib re-sending the file), so the window
     * is dropped instead of producing a bogus negative rate.
     */
    fun record(bytesUploaded: Long, atMs: Long = System.currentTimeMillis()) {
        if (bytesUploaded < 0) return
        if (bytesUploaded < peakBytes) reset()
        peakBytes = bytesUploaded

        val last = samples.lastOrNull()
        if (last != null && atMs < last.atMs) return // clock moved backwards; ignore
        if (last != null && last.bytes == bytesUploaded && atMs - last.atMs < MIN_SAMPLE_SPACING_MS) {
            return // no new information yet; avoid filling the window with duplicates
        }
        samples.addLast(Sample(atMs, bytesUploaded))
        while (samples.size > maxSamples) samples.removeFirst()
        evictOlderThan(atMs)
    }

    /**
     * Smoothed bytes/second over the window, or null when it cannot be
     * determined honestly.
     *
     * A window whose two most recent observations are identical means nothing
     * moved during the latest interval, so the reported rate is 0 rather than a
     * stale average from earlier in the window (the upload UI must show a stall
     * as a stall, not as progress that already happened).
     */
    fun bytesPerSecond(nowMs: Long = System.currentTimeMillis()): Double? {
        evictOlderThan(nowMs)
        val first = samples.firstOrNull() ?: return null
        val last = samples.lastOrNull() ?: return null
        val elapsedMs = last.atMs - first.atMs
        if (elapsedMs < MIN_SAMPLE_SPACING_MS) return null
        if (samples.size >= 2 && last.bytes == samples[samples.size - 2].bytes) return 0.0
        val deltaBytes = last.bytes - first.bytes
        if (deltaBytes <= 0) return 0.0
        return deltaBytes * 1000.0 / elapsedMs
    }

    /**
     * Remaining seconds at the current observed rate, or null when the total size
     * or the rate is unknown. Never returns a fabricated value.
     */
    fun remainingSeconds(
        bytesUploaded: Long,
        totalBytes: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? {
        if (totalBytes == null || totalBytes <= 0) return null
        val remainingBytes = totalBytes - bytesUploaded
        if (remainingBytes <= 0) return 0L
        val rate = bytesPerSecond(nowMs) ?: return null
        if (rate <= 0.0) return null
        return (remainingBytes / rate).toLong().coerceAtLeast(0L)
    }

    /** Clears the window (task switch, cancellation, retry). */
    fun reset() {
        samples.clear()
        peakBytes = 0L
    }

    private fun evictOlderThan(nowMs: Long) {
        while (samples.size > 2 && nowMs - samples.first().atMs > windowMs) {
            samples.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 5_000L
        const val MIN_SAMPLE_SPACING_MS = 200L
    }
}
