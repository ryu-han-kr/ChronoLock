package com.chronolock.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WeekStatusTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val rules = listOf(AppRule("video", "동영상", dailyLimitSeconds = 600), AppRule("chat", "메신저", maxOpenCount = 5))

    @Test fun classifiesKeptReachedAndUnknownDays() {
        val daily = mapOf(
            DailyKey("video", today) to DailyUsage(300_000),
            DailyKey("chat", today.minusDays(1)) to DailyUsage(1000, opens = 6, blockedAttempts = 1),
            DailyKey("video", today.minusDays(2)) to DailyUsage(600_000),
            DailyKey("unrelated", today.minusDays(3)) to DailyUsage(9_000_000),
        )
        val results = dayResults(daily, rules, today)
        assertEquals(7, results.size)
        assertEquals(today.minusDays(6), results.first().first)
        // today-3 only has an unrelated app, today-2 used the full limit, today-1 was blocked, today stayed under.
        assertEquals(listOf(DayResult.NO_RECORD, DayResult.REACHED, DayResult.REACHED, DayResult.KEPT),
            results.takeLast(4).map { it.second })
    }

    @Test fun noRulesMeansNoRecordedDays() {
        val daily = mapOf(DailyKey("video", today) to DailyUsage(1))
        assertTrue(dayResults(daily, emptyList(), today).all { it.second == DayResult.NO_RECORD })
    }
}
