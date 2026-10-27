package com.chronolock.domain

import java.time.LocalDate

data class UsageSuggestion(val rule: AppRule, val recipe: RuleRecipe, val recordedDays: Int, val opens: Int, val usageMillis: Long)

/** A local heuristic over recorded data, never a diagnosis or an automatic rule change. */
fun suggestUsageRule(daily: Map<DailyKey, DailyUsage>, rules: List<AppRule>, today: LocalDate): UsageSuggestion? {
    return rules.filter { it.enabled }.mapNotNull { rule ->
        val records = daily.filterKeys { it.packageName == rule.packageName && it.date in today.minusDays(6)..today }
        val opens = records.values.sumOf { it.opens }
        val millis = records.values.sumOf { it.usageMillis }
        if (records.size < 3 || opens < 15 || millis <= 0) return@mapNotNull null
        val average = millis / opens
        val recipe = when {
            average <= 120000 && rule.maxOpenCount == null -> RuleRecipe.LESS_CHECKING
            average >= 300000 && rule.sessionLimitSeconds == null -> RuleRecipe.SHORT_BREAK
            else -> return@mapNotNull null
        }
        UsageSuggestion(rule, recipe, records.size, opens, millis)
    }.maxByOrNull { it.opens }
}

/** Fill only absent limits; reviewing a recommendation must not weaken existing restrictions. */
fun suggestedDraft(rule: AppRule, recipe: RuleRecipe): RuleDraft {
    val current = RuleDraft.from(rule)
    return current.copy(
        session = current.session.ifBlank { recipe.draft.session },
        cooldown = current.cooldown.ifBlank { recipe.draft.cooldown },
        opens = current.opens.ifBlank { recipe.draft.opens },
    )
}

/** Total use in the 7 days ending [today] and in the 7 days before, from per-day totals. */
data class WeekTotals(val thisWeekMillis: Long, val lastWeekMillis: Long) {
    /** Change in percent against last week, or null when last week has nothing to compare with. */
    val changePercent: Int? get() = if (lastWeekMillis <= 0) null else Math.round((thisWeekMillis - lastWeekMillis) * 100.0 / lastWeekMillis).toInt()
}

/** The last seven days, oldest first, each paired with the same weekday a week earlier: (that day, a week before). */
fun weekDays(dayTotals: Map<java.time.LocalDate, Long>, today: java.time.LocalDate): List<Pair<Long, Long>> =
    (6L downTo 0L).map { back -> (dayTotals[today.minusDays(back)] ?: 0L) to (dayTotals[today.minusDays(back + 7)] ?: 0L) }

fun weekTotals(dayTotals: Map<java.time.LocalDate, Long>, today: java.time.LocalDate): WeekTotals {
    fun sum(from: Long, to: Long) = (from..to).sumOf { dayTotals[today.minusDays(it)] ?: 0L }
    return WeekTotals(sum(0, 6), sum(7, 13))
}

/**
 * The week (its Monday) whose report is due at [now]: from Sunday 18:00 until the end of Wednesday, once per week, so a
 * deferred background job still delivers it. [lastSentWeekStart] is the Monday of the week last reported.
 */
fun weeklyReportDue(now: java.time.LocalDateTime, lastSentWeekStart: java.time.LocalDate?): java.time.LocalDate? {
    val weekStart = when {
        now.dayOfWeek == java.time.DayOfWeek.SUNDAY && now.hour >= 18 -> now.toLocalDate().minusDays(6)
        now.dayOfWeek in setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY) ->
            now.toLocalDate().with(java.time.DayOfWeek.MONDAY).minusDays(7)
        else -> return null
    }
    return weekStart.takeIf { it != lastSentWeekStart }
}
