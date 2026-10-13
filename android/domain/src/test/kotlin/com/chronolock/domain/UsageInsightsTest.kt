package com.chronolock.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class UsageInsightsTest {
    private val today = LocalDate.of(2026, 10, 13)
    private val rule = AppRule("test.app", "Test", dailyLimitSeconds = 600)
    private fun records(usage: Long = 300000) = (0L..2L).associate {
        DailyKey(rule.packageName, today.minusDays(it)) to DailyUsage(usageMillis = usage, opens = 5)
    }

    @Test fun repeatedShortUsage_suggestsCountLimitWithActualEvidence() {
        val suggestion = suggestUsageRule(records(), listOf(rule), today)!!
        assertEquals(RuleRecipe.LESS_CHECKING, suggestion.recipe)
        assertEquals(15, suggestion.opens)
        assertEquals(3, suggestion.recordedDays)
        assertEquals(900000L, suggestion.usageMillis)
    }

    @Test fun longUsage_suggestsSessionLimit() {
        assertEquals(RuleRecipe.SHORT_BREAK, suggestUsageRule(records(1500000), listOf(rule), today)?.recipe)
    }

    @Test fun insufficientOrOutOfWindowData_doesNotProduceAdvice() {
        assertNull(suggestUsageRule(records().filterKeys { it.date == today }, listOf(rule), today))
        assertNull(suggestUsageRule(records().mapKeys { it.key.copy(date = it.key.date.minusDays(7)) }, listOf(rule), today))
        assertNull(suggestUsageRule(records().mapKeys { it.key.copy(date = it.key.date.plusDays(7)) }, listOf(rule), today))
    }

    @Test fun disabledOrAlreadyLimitedApps_areNotSuggested() {
        assertNull(suggestUsageRule(records(), listOf(rule.copy(enabled = false)), today))
        assertNull(suggestUsageRule(records(), listOf(rule.copy(maxOpenCount = 3)), today))
        assertNull(suggestUsageRule(records(1500000), listOf(rule.copy(sessionLimitSeconds = 120)), today))
    }

    @Test fun suggestion_preservesExistingStricterLimitsAndOnlyFillsMissingOnes() {
        val existing = rule.copy(sessionLimitSeconds = 60, cooldownSeconds = 1800)
        val proposed = suggestedDraft(existing, RuleRecipe.LESS_CHECKING).build(rule.packageName, rule.appName)
        assertEquals(600L, proposed.dailyLimitSeconds)
        assertEquals(60L, proposed.sessionLimitSeconds)
        assertEquals(1800L, proposed.cooldownSeconds)
        assertEquals(5, proposed.maxOpenCount)
    }
}
