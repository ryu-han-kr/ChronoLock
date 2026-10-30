package com.chronolock.domain

import java.time.LocalDate

/** A first limit for [app]: [averageMillis] a day lately, so a daily limit of [limitSeconds]. */
data class StarterLimit(val app: String, val averageMillis: Long, val limitSeconds: Long)

/**
 * After a first week of records, the [count] most-used [candidates] without a rule, each with a daily limit at about
 * 70% of its average, in 5-minute steps and never under 15 minutes. Empty with fewer than [MIN_DAYS] recorded days.
 */
fun starterLimits(usage: Map<DailyKey, Long>, candidates: Set<String>, today: LocalDate, count: Int = 3): List<StarterLimit> {
    val week = usage.filterKeys { it.date in today.minusDays(7)..today.minusDays(1) }
    val days = week.keys.map { it.date }.toSet().size
    if (days < MIN_DAYS) return emptyList()
    return week.entries.filter { it.key.packageName in candidates }
        .groupBy({ it.key.packageName }, { it.value }).mapValues { it.value.sum() / days }
        .filterValues { it >= MIN_AVERAGE_MILLIS }
        .entries.sortedByDescending { it.value }.take(count)
        .map { (app, average) ->
            val minutes = (average * 7 / 10 / 60_000L / 5 * 5).coerceAtLeast(15)
            StarterLimit(app, average, minutes * 60)
        }
}

private const val MIN_DAYS = 3
/** Apps used under 20 minutes a day are left alone. */
private const val MIN_AVERAGE_MILLIS = 20 * 60_000L
