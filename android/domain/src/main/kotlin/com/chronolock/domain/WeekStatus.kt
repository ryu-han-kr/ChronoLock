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
    return (days - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val rows = byApp.values.mapNotNull { rule -> daily[DailyKey(rule.packageName, date)]?.let { rule to it } }
        val result = when {
            rows.isEmpty() -> DayResult.NO_RECORD
            rows.any { (rule, usage) -> usage.blockedAttempts > 0 ||
                rule.dailyLimitSeconds?.let { usage.usageMillis >= it * 1000 } == true } -> DayResult.REACHED
            else -> DayResult.KEPT
        }
        date to result
    }
}
