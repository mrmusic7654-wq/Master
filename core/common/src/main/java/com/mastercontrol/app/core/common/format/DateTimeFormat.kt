package com.mastercontrol.app.core.common.format

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Timestamp rendering for the catalog and activity log.
 *
 * Timestamps are stored as epoch milliseconds (Room) / [Instant] (domain) in UTC
 * and rendered in the device zone. Unknown values render as an em dash rather
 * than as a fake date.
 */
object DateTimeFormat {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun date(epochMs: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        epochMs?.let { dateFormatter.withZone(zone).format(Instant.ofEpochMilli(it)) } ?: "—"

    fun dateTime(epochMs: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        epochMs?.let { dateTimeFormatter.withZone(zone).format(Instant.ofEpochMilli(it)) } ?: "—"

    fun time(epochMs: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        epochMs?.let { timeFormatter.withZone(zone).format(Instant.ofEpochMilli(it)) } ?: "—"

    fun instant(value: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        value?.let { dateTimeFormatter.withZone(zone).format(it) } ?: "—"

    /**
     * Relative age used by the dashboard/activity list ("just now", "5 min ago",
     * "3 h ago", "12 days ago"). Falls back to the absolute date past ~30 days so
     * old entries stay meaningful.
     */
    fun relative(epochMs: Long?, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochMs == null) return "—"
        val delta = nowMs - epochMs
        if (delta < 0) return dateTime(epochMs, zone)
        val seconds = delta / 1000
        return when {
            seconds < 45 -> "just now"
            seconds < 90 -> "1 min ago"
            seconds < 45 * 60 -> "${seconds / 60} min ago"
            seconds < 90 * 60 -> "1 h ago"
            seconds < 36 * 3600 -> "${seconds / 3600} h ago"
            seconds < 48 * 3600 -> "yesterday"
            seconds < 30L * 24 * 3600 -> "${Duration.ofSeconds(seconds).toDays()} days ago"
            else -> date(epochMs, zone)
        }
    }
}
