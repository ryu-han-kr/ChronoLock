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

    @Test fun streak_countsKeptDaysEndingTodayAndToleratesAnUnfinishedToday() {
        fun kept(days: Long) = DailyKey("video", today.minusDays(days)) to DailyUsage(60_000)
        val daily = mapOf(kept(1), kept(2), kept(3), DailyKey("video", today.minusDays(4)) to DailyUsage(600_000), kept(5))
        assertEquals(3, keptStreak(daily, rules, today))
        assertEquals(4, keptStreak(daily + kept(0), rules, today))
        // A reached limit today ends the streak at once.
        assertEquals(0, keptStreak(daily + (DailyKey("chat", today) to DailyUsage(blockedAttempts = 1)), rules, today))
        // A gap without records is not a success.
        assertEquals(1, keptStreak(mapOf(kept(1), kept(3)), rules, today))
        assertEquals(0, keptStreak(emptyMap(), rules, today))
    }

    @Test fun weekComparison_sumsBothWeeksAndNeedsAnEarlierRecord() {
        val daily = mapOf(
            DailyKey("video", today) to DailyUsage(30_000),
            DailyKey("chat", today.minusDays(6)) to DailyUsage(30_000),
            DailyKey("video", today.minusDays(7)) to DailyUsage(80_000),
            DailyKey("chat", today.minusDays(13)) to DailyUsage(40_000),
            DailyKey("video", today.minusDays(14)) to DailyUsage(999_000),
        )
        val comparison = compareWeeks(daily, today)
        assertEquals(WeekComparison(60_000, 120_000, 2), comparison)
        assertEquals(-50, comparison.changePercent)
        assertNull(compareWeeks(mapOf(DailyKey("video", today) to DailyUsage(1)), today).changePercent)
    }
}
