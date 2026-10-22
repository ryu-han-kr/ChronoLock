package com.chronolock.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RuleDraftTest {
    @Test fun shortBreakRecipe_combinesSessionAndCooldownWithoutDailyCap() {
        val rule = RuleRecipe.SHORT_BREAK.draft.build("test.app", "Test")
        assertEquals(300L, rule.sessionLimitSeconds)
        assertEquals(600L, rule.cooldownSeconds)
        assertNull(rule.dailyLimitSeconds)
    }

    @Test fun checkingRecipe_limitsBothCountAndSession() {
        val rule = RuleRecipe.LESS_CHECKING.draft.build("test.app", "Test")
        assertEquals(5, rule.maxOpenCount)
        assertEquals(180L, rule.sessionLimitSeconds)
    }

    @Test fun sleepRecipe_blocksAcrossMidnightAndReleasesAtSeven() {
        val rule = RuleRecipe.SLEEP.draft.build("test.app", "Test")
        val zone = ZoneId.of("Asia/Seoul")
        fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
        assertTrue(rule.schedule!!.contains(at("2026-10-13T23:00:00"), zone))
        assertTrue(rule.schedule!!.contains(at("2026-10-14T06:59:59"), zone))
        assertFalse(rule.schedule!!.contains(at("2026-10-14T07:00:00"), zone))
    }

    @Test fun draftRoundTrip_preservesAllEditableRulesAndDisabledState() {
        val rule = RuleDraft("1200", "4", "30", "300", true, "22:30", "06:15", 31, false).build("test.app", "Test")
        assertEquals(rule, RuleDraft.from(rule).build("test.app", "Test"))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), rule.schedule!!.days)
    }

    @Test fun disabledSchedule_ignoresItsUnfinishedInput() {
        val rule = RuleDraft(scheduleEnabled = false, start = "oops", end = "", daysMask = 0).build("test.app", "Test")
        assertNull(rule.schedule)
    }

    @Test fun scheduleOnly_isAValidRule() {
        assertNotNull(RuleDraft(daily = "", scheduleEnabled = true, daysMask = 1).build("test.app", "Test").schedule)
    }

    @Test fun newSchedule_hasNoDaysUntilUserSelectsThem() {
        val draft = RuleDraft().copy(scheduleEnabled = true)
        assertEquals(0, draft.daysMask)
        assertThrows(IllegalArgumentException::class.java) { draft.build("test.app", "Test") }
        assertEquals(setOf(DayOfWeek.WEDNESDAY), draft.copy(daysMask = 4).build("test.app", "Test").schedule!!.days)
    }

    @Test fun editingRuleWithoutSchedule_startsWithNoDaysSelected() {
        val rule = RuleDraft().build("test.app", "Test")
        assertEquals(0, RuleDraft.from(rule).daysMask)
    }

    @Test fun malformedTimes_areRejected() {
        listOf("24:00", "12:60", "7:00", "07:00:30", "", "a:b").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RuleDraft(scheduleEnabled = true, start = value).build("test.app", "Test") }
        }
    }

    @Test fun equalTimesAndMissingDays_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { RuleDraft(scheduleEnabled = true, end = "23:00").build("test.app", "Test") }
        assertThrows(IllegalArgumentException::class.java) { RuleDraft(scheduleEnabled = true, daysMask = 0).build("test.app", "Test") }
    }

    @Test fun invalidSessionLimits_areRejected() {
        listOf("0", "-1", "86401", "1.5", "999999999999999999999").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RuleDraft(session = value).build("test.app", "Test") }
        }
    }

    @Test fun emptyRules_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { RuleDraft(daily = "").build("test.app", "Test") }
    }
}
