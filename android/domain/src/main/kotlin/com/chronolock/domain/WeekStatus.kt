package com.chronolock.domain

import java.time.LocalDate

enum class DayResult { KEPT, REACHED, NO_RECORD }

/**
 * How each of the last [days] days (oldest first, ending [today]) went for the apps that currently have rules.
 * A day is REACHED when an app was blocked or used up its daily limit, KEPT when such apps were used without that,
 * and NO_RECORD when none of them were recorded — an unknown day is never counted as a success.
 */
fun dayResults(daily: Map<DailyKey, DailyUsage>, rules: List<AppRule>, today: LocalDate, days: Int = 7): List<Pair<LocalDate, DayResult>> {
    val byApp = rules.associateBy { it.packageName }
    val groups = rules.flatMap { it.groups }.distinctBy { it.id }.filter { it.enabled }
    return (days - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val rows = byApp.values.mapNotNull { rule -> daily[DailyKey(rule.packageName, date)]?.let { rule to it } }
        val result = when {
            rows.isEmpty() -> DayResult.NO_RECORD
            rows.any { (rule, usage) -> usage.blockedAttempts > 0 ||
                rule.dailyLimitOn(date)?.let { usage.usageMillis >= it * 1000 } == true } ||
                groups.any { group -> group.dailyLimitSeconds?.let { limit ->
                    group.members.keys.sumOf { daily[DailyKey(it, date)]?.usageMillis ?: 0 } >= limit * 1000 } == true } -> DayResult.REACHED
            else -> DayResult.KEPT
        }
        date to result
    }
}

/**
 * Consecutive days, ending today, on which the limited apps were used without reaching a limit.
 * Today still counts as part of a streak while it has no record yet, since the day is not over; any other
 * unrecorded or reached day ends the streak. At most [maxDays] days are examined.
 */
fun keptStreak(daily: Map<DailyKey, DailyUsage>, rules: List<AppRule>, today: LocalDate, maxDays: Int = 30): Int {
    val results = dayResults(daily, rules, today, maxDays).reversed()
    val past = if (results.firstOrNull()?.second == DayResult.NO_RECORD) results.drop(1) else results
    return past.takeWhile { it.second == DayResult.KEPT }.size
}

/** Recorded usage in the 7 days ending [today] against the 7 days before; [previousDays] counts recorded days in that earlier week. */
data class WeekComparison(val currentMillis: Long, val previousMillis: Long, val previousDays: Int) {
    /** Change in percent, or null when the earlier week has nothing to compare against. */
    val changePercent: Int? get() = if (previousDays == 0 || previousMillis == 0L) null
        else Math.round((currentMillis - previousMillis) * 100.0 / previousMillis).toInt()
}

fun compareWeeks(daily: Map<DailyKey, DailyUsage>, today: LocalDate): WeekComparison {
    val current = today.minusDays(6)..today
    val previous = today.minusDays(13)..today.minusDays(7)
    val earlier = daily.filterKeys { it.date in previous }
    return WeekComparison(daily.filterKeys { it.date in current }.values.sumOf { it.usageMillis },
        earlier.values.sumOf { it.usageMillis }, earlier.keys.map { it.date }.distinct().size)
}

/**
 * Completed days in a row, from yesterday back to [start], kept for a goal lock. Unlike the streak, a day without any
 * record counts as kept: not opening the limited apps at all is the goal. Today never counts until it is over.
 */
fun goalKeptDays(daily: Map<DailyKey, DailyUsage>, rules: List<AppRule>, start: LocalDate, today: LocalDate): Int {
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt()
    if (days <= 0) return 0
    return dayResults(daily, rules, today.minusDays(1), days).reversed().takeWhile { it.second != DayResult.REACHED }.size
}
