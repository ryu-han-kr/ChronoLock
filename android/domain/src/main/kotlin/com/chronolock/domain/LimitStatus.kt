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
    val now = clock?.epochMillis ?: return null
    val rule = rules[target]?.takeIf { it.enabled && !it.pausedAt(now) && !decision.denied } ?: return null
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
    val tightest = rules.values.filter { it.enabled && !it.pausedAt(clock.epochMillis) }
        .mapNotNull { rule -> remainingDailyMillis(rule)?.let { rule to it } }
        .minByOrNull { it.second }
    return TodaySummary(used, tightest?.first?.appName, tightest?.second)
}

/** What is left of one group's shared limits today. */
data class GroupLeft(val name: String, val timeLeftMillis: Long?, val opensLeft: Int?)

/** The limits of the app on screen and what is left of each, for the ongoing notification. */
data class LiveLimit(
    val packageName: String,
    val appName: String,
    val dailyLimitMillis: Long?,
    val dailyLeftMillis: Long?,
    val sessionLeftMillis: Long?,
    val opensLeft: Int?,
    val groups: List<GroupLeft>,
    /** Time spent in the app today, shown while it is in use. */
    val usedTodayMillis: Long = 0,
) {
    /** The running allowance that ends first; the notification counts down to it. */
    val nearestMillis: Long? get() = (listOfNotNull(dailyLeftMillis, sessionLeftMillis) + groups.mapNotNull { it.timeLeftMillis }).minOrNull()
}

/**
 * The live limits of the observed app while it is in use, or null when it has no rule, is paused or is already blocked.
 * An app limited only by schedules or a cooldown still gets one, with nothing left to count down.
 */
fun MonitorState.liveLimit(rules: Map<String, AppRule>): LiveLimit? {
    val target = observedPackage ?: return null
    val now = clock ?: return null
    val rule = rules[target]?.takeIf { it.enabled && !it.pausedAt(now.epochMillis) && !decision.denied } ?: return null
    val today = daily[DailyKey(target, now.date)] ?: DailyUsage()
    val session = active?.takeIf { it.packageName == target }?.usageMillis ?: 0
    val groups = rule.groups.filter { it.enabled }.map { group ->
        val used = groupUsage(group)
        GroupLeft(group.name, group.dailyLimitSeconds?.let { (it * 1000 - used.usageMillis).coerceAtLeast(0) },
            group.maxOpenCount?.let { (it - used.opens).coerceAtLeast(0) })
    }
    val live = LiveLimit(target, rule.appName, rule.dailyLimitSeconds?.times(1000), remainingDailyMillis(rule),
        rule.sessionLimitSeconds?.let { (it * 1000 - session).coerceAtLeast(0) }, rule.maxOpenCount?.let { (it - today.opens).coerceAtLeast(0) },
        groups.filter { it.timeLeftMillis != null || it.opensLeft != null }, today.usageMillis)
    return live
}
