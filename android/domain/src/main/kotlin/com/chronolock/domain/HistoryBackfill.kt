package com.chronolock.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One app's foreground time in an OS usage-stats bucket (daily, weekly or monthly), spanning [firstMillis, lastMillis]. */
data class UsageBucket(val app: String, val firstMillis: Long, val lastMillis: Long, val foregroundMillis: Long)

/**
 * Estimates per-day usage for the days in [gapStart, gapEnd) that have no event records, from coarser OS buckets.
 *
 * [bucketsByInterval] is ordered finest first (daily, weekly, monthly). A gap day is filled from the finest interval that has
 * any bucket on it, so coarser data never double counts a day a finer bucket already explained. Within one bucket, the time
 * already known from [recorded] days is subtracted and the rest is shared evenly over the bucket's unknown days; only the
 * share that falls on gap days is returned.
 */
fun estimateGapUsage(
    bucketsByInterval: List<List<UsageBucket>>,
    recorded: Map<String, Map<LocalDate, Long>>,
    gapStart: LocalDate,
    gapEnd: LocalDate,
    zone: ZoneId,
): Map<DailyKey, Long> {
    if (!gapStart.isBefore(gapEnd)) return emptyMap()
    fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val result = mutableMapOf<DailyKey, Long>()
    var uncovered = generateSequence(gapStart) { it.plusDays(1) }.takeWhile { it.isBefore(gapEnd) }.toSet()
    for (buckets in bucketsByInterval) {
        if (uncovered.isEmpty()) break
        val coveredNow = mutableSetOf<LocalDate>()
        for (bucket in buckets) {
            if (bucket.lastMillis < bucket.firstMillis) continue
            val days = generateSequence(date(bucket.firstMillis)) { it.plusDays(1) }
                .takeWhile { !it.isAfter(date(bucket.lastMillis)) }.toList()
            val gapDays = days.filter { it in uncovered }
            if (gapDays.isEmpty()) continue
            coveredNow += gapDays
            val known = recorded[bucket.app].orEmpty()
            val knownDays = days.filter { it in known }
            val remaining = (bucket.foregroundMillis - knownDays.sumOf { known.getValue(it) }).coerceAtLeast(0)
            val unknownDays = days.size - knownDays.size
            if (remaining == 0L || unknownDays <= 0) continue
            val perDay = remaining / unknownDays
            if (perDay == 0L) continue
            gapDays.forEach { day ->
                val key = DailyKey(bucket.app, day)
                result[key] = (result[key] ?: 0) + perDay
            }
        }
        uncovered = uncovered - coveredNow
    }
    return result
}
