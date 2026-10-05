package com.drivelink.core.domain

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** Short age text for a timestamp of the API, for example "3 days ago". */
object TimeAgo {
    /**
     * Returns "just now", "5 min ago", "2 hr ago" or "3 days ago".
     * Returns null when [iso] is not an ISO 8601 instant.
     */
    fun describe(iso: String, now: Instant = Instant.now()): String? {
        val then = try {
            Instant.parse(iso)
        } catch (e: DateTimeParseException) {
            return null
        }
        val age = Duration.between(then, now)
        return when {
            age.toMinutes() < 1 -> "just now"
            age.toMinutes() < 60 -> "${age.toMinutes()} min ago"
            age.toHours() < 24 -> "${age.toHours()} hr ago"
            age.toDays() == 1L -> "1 day ago"
            else -> "${age.toDays()} days ago"
        }
    }
}
