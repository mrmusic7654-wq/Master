package com.mastercontrol.app.domain.id

/**
 * Permanent Video ID system (e.g. VID-000001).
 *
 * IDs are unique, never reused after deletion, and allocated transactionally
 * against a monotonic database sequence (see [VideoIdAllocator]). The
 * human-readable ID is the stable application key; a numeric auto-increment
 * row id is used internally by the database.
 */
object VideoId {
    const val PREFIX = "VID"
    const val DIGITS = 6
    private val PATTERN = Regex("^${PREFIX}-(\\d{$DIGITS,})$")

    /** Formats a zero-based sequence number into its permanent ID. */
    fun format(sequence: Long): String {
        require(sequence >= 0) { "Video ID sequence must be >= 0" }
        return "$PREFIX-${sequence.toString().padStart(DIGITS, '0')}"
    }

    /** Returns the numeric sequence encoded in an existing Video ID, or null. */
    fun parseSequence(videoId: String): Long? =
        PATTERN.matchEntire(videoId)?.groupValues?.get(1)?.toLongOrNull()

    fun isValid(videoId: String): Boolean = PATTERN.matches(videoId)

    /** Next ID after the given one in collation order (for tests/UI preview only). */
    fun nextAfter(lastVideoId: String?): String {
        val base = parseSequence(lastVideoId) ?: -1L
        return format(base + 1)
    }
}

/**
 * Allocates the next permanent ID. Implementations must be transactional and
 * collision-safe (e.g. a Room transaction incrementing a persisted counter).
 */
interface VideoIdAllocator {
    suspend fun allocate(): String
}
