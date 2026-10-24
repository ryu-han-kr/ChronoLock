package com.chronolock.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class AppRule(
    val packageName: String,
    val appName: String,
    val dailyLimitSeconds: Long? = null,
    val maxOpenCount: Int? = null,
    val cooldownSeconds: Long? = null,
    val sessionLimitSeconds: Long? = null,
    val schedules: List<Schedule> = emptyList(),
    val focusUntilEpochMillis: Long? = null,
    val enabled: Boolean = true,
    /** The rule is suspended until this instant and switches itself back on afterwards. */
    val pausedUntilEpochMillis: Long? = null,
    /** The shared-limit groups this app belongs to; attached for evaluation, never stored with the rule. */
    val groups: List<AppGroup> = emptyList(),
    /** A breathing pause before the app opens, in seconds; it grows with each open of the day (see [pauseDelaySeconds]). */
    val pauseSeconds: Int? = null,
    /** While the app stays open, ask every this many minutes whether to keep going. */
    val checkInMinutes: Int? = null,
) {
    init {
        require(packageName.isNotBlank() && appName.isNotBlank())
        require(dailyLimitSeconds == null || dailyLimitSeconds in 1..86400)
        require(sessionLimitSeconds == null || sessionLimitSeconds in 1..86400)
        require(maxOpenCount == null || maxOpenCount in 1..10000)
        require(cooldownSeconds == null || cooldownSeconds in 1..86400)
        require(schedules.size <= MAX_SCHEDULES)
        require(pauseSeconds == null || pauseSeconds in 1..30)
        require(checkInMinutes == null || checkInMinutes in 1..120)
    }

    fun pausedAt(epochMillis: Long) = pausedUntilEpochMillis?.let { epochMillis < it } == true

    companion object { const val MAX_SCHEDULES = 5 }
}

/** The pause before opening: [base] seconds, two more for every earlier open today, at most 30. */
fun pauseDelaySeconds(base: Int, opensToday: Int): Int = (base + 2 * opensToday.coerceAtLeast(0)).coerceAtMost(30)

/** Overnight schedules belong to the day on which the window starts. */
data class Schedule(val start: LocalTime, val end: LocalTime, val days: Set<DayOfWeek>) {
    init { require(start != end && days.isNotEmpty()) }
    fun contains(epochMillis: Long, zone: ZoneId): Boolean {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val time = local.toLocalTime()
        return if (start < end) {
            local.dayOfWeek in days && time >= start && time < end
        } else {
            (local.dayOfWeek in days && time >= start) ||
                (local.minusDays(1).dayOfWeek in days && time < end)
        }
    }
}

enum class Reason { FOCUS, SCHEDULE, DAILY_USAGE, SESSION_USAGE, OPEN_COUNT, COOLDOWN, GROUP_USAGE, GROUP_OPENS }
enum class DecisionType { ALLOW, WARN, BLOCK, COOLDOWN }
data class RuleDecision(val type: DecisionType, val reasons: List<Reason> = emptyList(), val retryAfterSeconds: Long? = null) {
    val denied: Boolean get() = type == DecisionType.BLOCK || type == DecisionType.COOLDOWN
    companion object { val Allow = RuleDecision(DecisionType.ALLOW) }
}

data class RuleContext(
    val nowEpochMillis: Long,
    val zone: ZoneId,
    val dailyUsageMillis: Long = 0,
    val sessionUsageMillis: Long = 0,
    val allowedOpens: Int = 0,
    val entering: Boolean = true,
    val sinceLastCloseMillis: Long? = null,
    val safetyAllowlisted: Boolean = false,
    val emergencyOverride: Boolean = false,
    /** Today's usage and allowed opens summed over each of the rule's groups (by id), including this app. */
    val groupUsage: Map<Long, DailyUsage> = emptyMap(),
)

class RuleEngine {
    fun evaluate(context: RuleContext, rule: AppRule?): RuleDecision {
        if (rule == null || !rule.enabled || context.safetyAllowlisted || context.emergencyOverride ||
            rule.pausedAt(context.nowEpochMillis)) return RuleDecision.Allow
        val reasons = buildList {
            if (rule.focusUntilEpochMillis?.let { context.nowEpochMillis < it } == true) add(Reason.FOCUS)
            if (rule.schedules.any { it.contains(context.nowEpochMillis, context.zone) }) add(Reason.SCHEDULE)
            if (rule.dailyLimitSeconds?.let { context.dailyUsageMillis >= it * 1000 } == true) add(Reason.DAILY_USAGE)
            if (rule.sessionLimitSeconds?.let { context.sessionUsageMillis >= it * 1000 } == true) add(Reason.SESSION_USAGE)
            if (context.entering && rule.maxOpenCount?.let { context.allowedOpens >= it } == true) add(Reason.OPEN_COUNT)
            if (context.entering && rule.cooldownSeconds != null && context.sinceLastCloseMillis != null &&
                context.sinceLastCloseMillis < rule.cooldownSeconds * 1000) add(Reason.COOLDOWN)
            // Every group the app is in must still have room; one used-up group is enough to block.
            rule.groups.forEach { group ->
                val used = context.groupUsage[group.id] ?: DailyUsage()
                if (group.dailyLimitSeconds?.let { used.usageMillis >= it * 1000 } == true) add(Reason.GROUP_USAGE)
                if (context.entering && group.maxOpenCount?.let { used.opens >= it } == true) add(Reason.GROUP_OPENS)
                if (group.schedules.any { it.contains(context.nowEpochMillis, context.zone) }) add(Reason.SCHEDULE)
            }
        }.distinct()
        if (reasons.isEmpty()) return RuleDecision.Allow
        val cooldownOnly = reasons == listOf(Reason.COOLDOWN)
        return RuleDecision(
            if (cooldownOnly) DecisionType.COOLDOWN else DecisionType.BLOCK,
            reasons,
            if (cooldownOnly) ((rule.cooldownSeconds!! * 1000 - context.sinceLastCloseMillis!! + 999) / 1000) else null,
        )
    }
}
