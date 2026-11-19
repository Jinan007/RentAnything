package com.clippost.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * YouTube Data API quota, as an estimate.
 *
 * A new Google Cloud project gets 10,000 units per day and one videos.insert costs 1,600 units,
 * so about 6 uploads a day. The day resets at midnight Pacific Time.
 * If Google raises your quota, change DAILY_UNITS.
 */
object Quota {
    const val DAILY_UNITS = 10_000
    const val UPLOAD_COST = 1_600
    const val UPLOADS_PER_DAY = DAILY_UNITS / UPLOAD_COST

    private val PACIFIC = ZoneId.of("America/Los_Angeles")

    /** The start of the current quota day (midnight Pacific Time), in epoch milliseconds. */
    fun dayStartMillis(now: Long = System.currentTimeMillis()): Long =
        Instant.ofEpochMilli(now).atZone(PACIFIC).truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()

    /** When the quota resets next (midnight Pacific Time), in epoch milliseconds. */
    fun nextResetMillis(now: Long = System.currentTimeMillis()): Long =
        Instant.ofEpochMilli(now).atZone(PACIFIC).truncatedTo(ChronoUnit.DAYS).plusDays(1)
            .toInstant().toEpochMilli()

    /** The next reset, written in the phone's own time zone. */
    fun resetText(now: Long = System.currentTimeMillis()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(nextResetMillis(now)))

    /** How many uploads finished since the quota day started. */
    fun usedToday(clips: List<Clip>): Int {
        val start = dayStartMillis()
        return clips.count { it.upload is UploadState.Done && (it.completedAt ?: 0L) >= start }
    }

    fun leftToday(clips: List<Clip>): Int = (UPLOADS_PER_DAY - usedToday(clips)).coerceAtLeast(0)
}
