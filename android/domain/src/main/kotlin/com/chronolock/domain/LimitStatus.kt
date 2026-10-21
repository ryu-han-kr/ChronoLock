package com.chronolock.domain

/** Milliseconds of [rule]'s daily allowance left on the state's current date, or null without a daily limit. */
fun MonitorState.remainingDailyMillis(rule: AppRule): Long? {
    val limit = rule.dailyLimitSeconds ?: return null
    val date = clock?.date ?: return null
    val used = daily[DailyKey(rule.packageName, date)]?.usageMillis ?: 0
    return (limit * 1000 - used).coerceAtLeast(0)
}

/**
 * The app in the foreground whose daily allowance just dropped to [thresholdMillis] or less, once per app and day.
 * Limits no longer than the threshold are skipped: warning at the moment such an app opens would only be noise.
 */
fun MonitorState.limitWarning(rules: Map<String, AppRule>, warned: Set<DailyKey>, thresholdMillis: Long = 5 * 60_000L): DailyKey? {
    val target = observedPackage ?: return null
    val rule = rules[target]?.takeIf { it.enabled && !decision.denied } ?: return null
    val limitMillis = (rule.dailyLimitSeconds ?: return null) * 1000
    if (limitMillis <= thresholdMillis) return null
    val remaining = remainingDailyMillis(rule) ?: return null
    val key = DailyKey(target, clock!!.date)
    return key.takeIf { remaining in 1..thresholdMillis && it !in warned }
}

data class TodaySummary(val usedMillis: Long, val tightestApp: String?, val tightestRemainingMillis: Long?)

/** Today's usage of limited apps and the enabled daily limit closest to running out, for glanceable surfaces. */
fun MonitorState.todaySummary(rules: Map<String, AppRule>): TodaySummary {
    val date = clock?.date ?: return TodaySummary(0, null, null)
    val used = rules.keys.sumOf { daily[DailyKey(it, date)]?.usageMillis ?: 0 }
    val tightest = rules.values.filter { it.enabled }
        .mapNotNull { rule -> remainingDailyMillis(rule)?.let { rule to it } }
        .minByOrNull { it.second }
    return TodaySummary(used, tightest?.first?.appName, tightest?.second)
}
