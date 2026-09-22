package com.mastercontrol.app.core.common.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Presentation formatting helpers.
 *
 * These are pure functions over real values: nothing here invents data, it only
 * renders measurements produced by the media/transfer subsystems. Kept free of
 * Android types so they are unit-testable on the JVM (see core/common tests).
 */
object Formatters {

    private const val UNIT_STEP = 1024.0
    private val BINARY_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    private val RATE_UNITS = arrayOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s")

    /** "1.5 GB", "842 KB", "0 B". Negative/unknown values render as an em dash. */
    fun bytes(sizeBytes: Long?): String {
        if (sizeBytes == null) return "—"
        if (sizeBytes == 0L) return "0 B"
        val negative = sizeBytes < 0
        val value = abs(sizeBytes).toDouble()
        val exponent = (ln(value) / ln(UNIT_STEP)).toInt().coerceIn(0, BINARY_UNITS.lastIndex)
        val scaled = value / UNIT_STEP.pow(exponent)
        val decimals = if (exponent == 0 || scaled >= 100) 0 else 1
        val rendered = if (decimals == 0) scaled.roundToInt().toString() else "%.1f".formatLocale(scaled)
        return buildString {
            if (negative) append('-')
            append(rendered)
            append(' ')
            append(BINARY_UNITS[exponent])
        }
    }

    /** Transfer rate, e.g. "12.4 MB/s". Zero/unknown renders as "—". */
    fun rate(bytesPerSecond: Double?): String {
        if (bytesPerSecond == null || bytesPerSecond <= 0.0 || bytesPerSecond.isNaN() || bytesPerSecond.isInfinite()) {
            return "—"
        }
        val exponent = (ln(bytesPerSecond) / ln(UNIT_STEP)).toInt().coerceIn(0, RATE_UNITS.lastIndex)
        val scaled = bytesPerSecond / UNIT_STEP.pow(exponent)
        val decimals = if (exponent == 0 || scaled >= 100) 0 else 1
        val rendered = if (decimals == 0) scaled.roundToInt().toString() else "%.1f".formatLocale(scaled)
        return "$rendered ${RATE_UNITS[exponent]}"
    }

    /**
     * Wall-clock duration.
     *
     * - under one hour: `mm:ss` ("04:07")
     * - one hour or more: `h:mm:ss` ("2:04:07")
     * - unknown (null): em dash
     */
    fun duration(durationMs: Long?): String {
        if (durationMs == null || durationMs < 0) return "—"
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".formatLocale(hours, minutes, seconds)
        } else {
            "%02d:%02d".formatLocale(minutes, seconds)
        }
    }

    /** Countdown/ETA rendering: `mm:ss`, switching to `h:mm:ss` past one hour. */
    fun eta(remainingSeconds: Long?): String =
        if (remainingSeconds == null || remainingSeconds < 0) "—" else duration(remainingSeconds * 1000)

    /** Percentage with no decimal places, clamped to 0..100 ("82%"). */
    fun percent(fraction: Float): String {
        val clamped = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        return "${(clamped * 100).roundToInt()}%"
    }

    /** "1920 × 1080" or em dash when either dimension is unknown. */
    fun resolution(width: Int?, height: Int?): String =
        if (width == null || height == null || width <= 0 || height <= 0) "—" else "$width × $height"

    /** Common shorthand for a video height ("1080p", "720p"); em dash when unknown. */
    fun resolutionLabel(width: Int?, height: Int?): String {
        if (height == null || height <= 0) return "—"
        return "${height}p"
    }

    /** "23.98 fps"; em dash for unknown or non-positive frame rates. */
    fun frameRate(frameRate: Double?): String =
        if (frameRate == null || frameRate <= 0.0 || frameRate.isNaN()) "—" else "%.2f fps".formatLocale(frameRate)

    /** Container/codec label derived from a MIME type ("video/mp4" → "MP4"). */
    fun formatFromMime(mimeType: String?): String {
        if (mimeType.isNullOrBlank()) return "—"
        val subType = mimeType.substringAfterLast('/').trim()
        if (subType.isEmpty() || subType == "*") return mimeType.uppercase()
        return subType
            .removePrefix("x-")
            .split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.uppercase() }
    }

    /** Compact count rendering for dashboard tiles ("1.2k", "3.4M"). */
    fun compactCount(value: Long): String = when {
        value < 1_000 -> value.toString()
        value < 1_000_000 -> trimZero("%.1f".formatLocale(value / 1_000.0)) + "k"
        value < 1_000_000_000 -> trimZero("%.1f".formatLocale(value / 1_000_000.0)) + "M"
        else -> trimZero("%.1f".formatLocale(value / 1_000_000_000.0)) + "B"
    }

    private fun trimZero(value: String): String = if (value.endsWith(".0")) value.dropLast(2) else value

    /**
     * Locale-stable numeric rendering. Measurements are rendered with
     * [Locale.ROOT] so a rate is always "12.4 MB/s" and never "12,4 MB/s":
     * these strings are also compared in tests and parsed by diagnostics.
     */
    private fun String.formatLocale(vararg args: Any?): String = String.format(Locale.ROOT, this, *args)
}
