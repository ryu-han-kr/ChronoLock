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
        assertTrue(rule.schedules.single().contains(at("2026-10-13T23:00:00"), zone))
        assertTrue(rule.schedules.single().contains(at("2026-10-14T06:59:59"), zone))
        assertFalse(rule.schedules.single().contains(at("2026-10-14T07:00:00"), zone))
    }

    @Test fun draftRoundTrip_preservesAllEditableRulesAndDisabledState() {
        val rule = RuleDraft("1200", "4", "30", "300", true, listOf(ScheduleDraft("22:30", "06:15", 31), ScheduleDraft("10:00", "12:00", 96)), false).build("test.app", "Test")
        assertEquals(rule, RuleDraft.from(rule).build("test.app", "Test"))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), rule.schedules.first().days)
    }

    @Test fun disabledSchedule_ignoresItsUnfinishedInput() {
        val rule = RuleDraft(scheduleEnabled = false, schedules = listOf(ScheduleDraft("oops", "", 0))).build("test.app", "Test")
        assertTrue(rule.schedules.isEmpty())
    }

    @Test fun scheduleOnly_isAValidRule() {
        assertEquals(1, RuleDraft(daily = "", scheduleEnabled = true, schedules = listOf(ScheduleDraft(daysMask = 1))).build("test.app", "Test").schedules.size)
    }

    @Test fun newSchedule_hasNoDaysUntilUserSelectsThem() {
        val draft = RuleDraft().copy(scheduleEnabled = true)
        assertEquals(0, draft.schedules.single().daysMask)
        assertThrows(IllegalArgumentException::class.java) { draft.build("test.app", "Test") }
        assertEquals(setOf(DayOfWeek.WEDNESDAY), draft.copy(schedules = listOf(ScheduleDraft(daysMask = 4))).build("test.app", "Test").schedules.single().days)
    }

    @Test fun editingRuleWithoutSchedule_startsWithNoDaysSelected() {
        val rule = RuleDraft().build("test.app", "Test")
        assertEquals(0, RuleDraft.from(rule).schedules.single().daysMask)
    }

    @Test fun malformedTimes_areRejected() {
        listOf("24:00", "12:60", "7:00", "07:00:30", "", "a:b").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RuleDraft(scheduleEnabled = true, schedules = listOf(ScheduleDraft(start = value, daysMask = 1))).build("test.app", "Test") }
        }
    }

    @Test fun equalTimesAndMissingDays_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { RuleDraft(scheduleEnabled = true, schedules = listOf(ScheduleDraft(end = "23:00", daysMask = 1))).build("test.app", "Test") }
        assertThrows(IllegalArgumentException::class.java) { RuleDraft(scheduleEnabled = true, schedules = listOf(ScheduleDraft(daysMask = 0))).build("test.app", "Test") }
    }

    @Test fun invalidSessionLimits_areRejected() {
        listOf("0", "-1", "86401", "1.5", "999999999999999999999").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RuleDraft(session = value).build("test.app", "Test") }
        }
    }

    @Test fun emptyRules_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { RuleDraft(daily = "").build("test.app", "Test") }
    }

    @Test fun multipleSchedules_blockInEitherWindowAndDropDuplicates() {
        val zone = ZoneId.of("Asia/Seoul")
        fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
        val rule = RuleDraft(daily = "", scheduleEnabled = true, schedules = listOf(
            ScheduleDraft("09:00", "18:00", ScheduleDraft.WEEKDAYS), ScheduleDraft("23:00", "07:00", ScheduleDraft.EVERY_DAY),
            ScheduleDraft("09:00", "18:00", ScheduleDraft.WEEKDAYS))).build("test.app", "Test")
        assertEquals(2, rule.schedules.size)
        val engine = RuleEngine()
        fun blocked(value: String) = engine.evaluate(RuleContext(at(value), zone), rule).denied
        assertTrue(blocked("2026-10-21T10:00:00")) // Wednesday, work window
        assertFalse(blocked("2026-10-24T10:00:00")) // Saturday
        assertTrue(blocked("2026-10-24T23:30:00")) // every-day sleep window
        assertFalse(blocked("2026-10-21T20:00:00"))
    }

    @Test fun tooManySchedules_areRejected() {
        val six = List(6) { ScheduleDraft("0$it:00", "0$it:30", 1) }
        assertThrows(IllegalArgumentException::class.java) { RuleDraft(scheduleEnabled = true, schedules = six).build("test.app", "Test") }
    }
}
