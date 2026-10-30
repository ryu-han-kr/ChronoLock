package com.chronolock.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One focus as it went: from [start] to [end], and whether it ran to its planned end ([finished]) or was stopped. */
data class FocusRun(val start: Long, val end: Long, val finished: Boolean)

/** This week's focus: time spent in it, focuses run to the end, and the days with any focus. */
data class FocusWeek(val millis: Long, val finished: Int, val days: Int)

/** Focuses kept on the device for [KEEP_DAYS] days, stored as `start:end:1;…` (1 when finished). */
object FocusLog {
    const val KEEP_DAYS = 60L

    fun decode(text: String?): List<FocusRun> = text.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split(':')
        if (parts.size != 3) return@mapNotNull null
        val start = parts[0].toLongOrNull() ?: return@mapNotNull null
        val end = parts[1].toLongOrNull()?.takeIf { it > start } ?: return@mapNotNull null
        FocusRun(start, end, parts[2] == "1")
    }

    fun encode(runs: List<FocusRun>): String = runs.joinToString(";") { "${it.start}:${it.end}:${if (it.finished) 1 else 0}" }

    /** [run] added, once per start, with runs older than [KEEP_DAYS] dropped. */
    fun add(runs: List<FocusRun>, run: FocusRun, now: Long): List<FocusRun> =
        (runs.filter { it.start != run.start } + run).filter { now - it.end < KEEP_DAYS * 86_400_000L }.sortedBy { it.start }

    /** The 7 days ending [today]. */
    fun week(runs: List<FocusRun>, today: LocalDate, zone: ZoneId): FocusWeek {
        val from = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inWeek = runs.filter { it.end > from && it.start < until }
        return FocusWeek(
            inWeek.sumOf { minOf(it.end, until) - maxOf(it.start, from) },
            inWeek.count { it.finished },
            inWeek.map { Instant.ofEpochMilli(maxOf(it.start, from)).atZone(zone).toLocalDate() }.toSet().size,
        )
    }
}
