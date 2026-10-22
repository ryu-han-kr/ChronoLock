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
