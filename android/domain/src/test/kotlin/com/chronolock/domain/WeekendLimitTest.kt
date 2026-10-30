package com.chronolock.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekendLimitTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val friday = LocalDate.of(2026, 10, 30)
    private val saturday = friday.plusDays(1)
    private val rule = AppRule("video", "Video", dailyLimitSeconds = 3600, weekendLimitSeconds = 7200)
    private fun at(date: LocalDate) = LocalDateTime.of(date, java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    @Test fun weekendTakesItsOwnLimit_andBlankMeansEveryDay() {
        assertEquals(3600L, rule.dailyLimitOn(friday))
        assertEquals(7200L, rule.dailyLimitOn(saturday))
        assertEquals(7200L, rule.dailyLimitOn(saturday.plusDays(1)))
        assertEquals(3600L, rule.copy(weekendLimitSeconds = null).dailyLimitOn(saturday))
    }

    @Test fun engine_blocksAtTheDaysOwnLimit() {
        val engine = RuleEngine()
        val ninetyMinutes = 90 * 60_000L
        assertTrue(engine.evaluate(RuleContext(at(friday), zone, ninetyMinutes), rule).denied)
        assertFalse(engine.evaluate(RuleContext(at(saturday), zone, ninetyMinutes), rule).denied)
    }

    @Test fun raisingTheWeekendLimit_loosens_loweringDoesNot() {
        assertTrue(Strictness.loosens(rule, rule.copy(weekendLimitSeconds = 9000), 0))
        assertTrue(Strictness.loosens(rule.copy(weekendLimitSeconds = null), rule, 0))
        assertFalse(Strictness.loosens(rule, rule.copy(weekendLimitSeconds = 5400), 0))
    }

    @Test fun draft_keepsTheWeekendLimit() {
        val built = RuleDraft(daily = "3600", weekend = "7200").build("video", "Video")
        assertEquals(7200L, built.weekendLimitSeconds)
        assertEquals("7200", RuleDraft.from(built).weekend)
        assertEquals(7200L, RuleDraft(daily = "", weekend = "7200").build("video", "Video").weekendLimitSeconds)
    }
}
