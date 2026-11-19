package com.clippost.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * When to publish Shorts so that both UK and US viewers are awake and watching,
 * with a gap between every video.
 *
 * Why these times: the UK is 5 hours ahead of New York and 8 ahead of Los Angeles (4 and 7 for a few
 * weeks a year, when the clocks change on different dates). So a UK evening is a US afternoon.
 *   17:30 UK = 12:30 New York = 09:30 Los Angeles   (UK after work, US lunch)  <- best overlap
 *   20:30 UK = 15:30 New York = 12:30 Los Angeles   (UK prime time, US afternoon)
 *   13:00 UK = 08:00 New York = 05:00 Los Angeles   (UK lunch, US morning commute)
 *
 * This comes from time zones and common viewing habits, NOT from your own channel's numbers. Your
 * audience may differ, so treat it as a sensible start. The times and the gap are the constants below.
 */
object PostingPlan {

    val UK: ZoneId = ZoneId.of("Europe/London")
    val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    val LOS_ANGELES: ZoneId = ZoneId.of("America/Los_Angeles")

    /** The publishing times in UK local time, best first. "Videos per day" uses the first N of them. */
    private val SLOTS = listOf(LocalTime.of(17, 30), LocalTime.of(20, 30), LocalTime.of(13, 0))

    const val MAX_PER_DAY = 3

    /** Two scheduled videos are never closer together than this. */
    const val MIN_GAP_MS = 3 * 60 * 60 * 1000L

    /** The slot times for a given number of videos per day, earliest first, as text like "13:00, 17:30". */
    fun slotText(perDay: Int): String =
        SLOTS.take(perDay.coerceIn(1, MAX_PER_DAY)).sorted().joinToString(", ") { it.toString() } + " UK"

    /**
     * The next free publishing slot.
     * @param taken times (epoch milliseconds) already used by other videos
     * @param notBefore only slots after this moment (used to move to the next slot)
     */
    fun nextSlot(now: Long, perDay: Int, taken: List<Long>, notBefore: Long = 0L): Long {
        val earliest = maxOf(now + MIN_SCHEDULE_LEAD_MILLIS, notBefore + 1)
        val times = SLOTS.take(perDay.coerceIn(1, MAX_PER_DAY)).sorted()
        val firstDay = LocalDate.ofInstant(Instant.ofEpochMilli(now), UK)

        for (offset in 0L..120L) {
            val day = firstDay.plusDays(offset)
            for (time in times) {
                val candidate = day.atTime(time).atZone(UK).toInstant().toEpochMilli()
                if (candidate < earliest) continue
                if (taken.any { abs(it - candidate) < MIN_GAP_MS }) continue
                return candidate
            }
        }
        return earliest // never reached in practice
    }

    /** "Fri 17:30 UK · 12:30 New York · 09:30 Los Angeles" */
    fun describe(millis: Long): String {
        val instant = Instant.ofEpochMilli(millis)
        fun at(zone: ZoneId, pattern: String) = DateTimeFormatter.ofPattern(pattern).withZone(zone).format(instant)
        return "${at(UK, "EEE HH:mm")} UK · ${at(NEW_YORK, "HH:mm")} New York · ${at(LOS_ANGELES, "HH:mm")} Los Angeles"
    }
}
