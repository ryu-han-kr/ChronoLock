package com.chronolock.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private val engine = RuleEngine()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-10-08T09:00:00Z").toEpochMilli()
    private fun context() = RuleContext(now, zone)
    private fun rule() = AppRule("test.app", "Test")

    @Test fun dailyLimit_allowsBeforeThreshold() {
        assertFalse(engine.evaluate(context().copy(dailyUsageMillis = 59999), rule().copy(dailyLimitSeconds = 60)).denied)
    }
    @Test fun dailyLimit_blocksAtExactThreshold() {
        assertEquals(listOf(Reason.DAILY_USAGE), engine.evaluate(context().copy(dailyUsageMillis = 60000), rule().copy(dailyLimitSeconds = 60)).reasons)
    }
    @Test fun openCount_allowsThirdOpen() {
        assertFalse(engine.evaluate(context().copy(allowedOpens = 2), rule().copy(maxOpenCount = 3)).denied)
    }
    @Test fun openCount_blocksFourthOpen() {
        assertTrue(engine.evaluate(context().copy(allowedOpens = 3), rule().copy(maxOpenCount = 3)).denied)
    }
    @Test fun openCount_doesNotInterruptThirdSession() {
        assertFalse(engine.evaluate(context().copy(allowedOpens = 3, entering = false), rule().copy(maxOpenCount = 3)).denied)
    }
    @Test fun cooldown_blocksAndRoundsUpRemainingTime() {
        val result = engine.evaluate(context().copy(sinceLastCloseMillis = 29001), rule().copy(cooldownSeconds = 30))
        assertEquals(DecisionType.COOLDOWN, result.type)
        assertEquals(1L, result.retryAfterSeconds)
    }
    @Test fun cooldown_allowsAtExactExpiry() {
        assertFalse(engine.evaluate(context().copy(sinceLastCloseMillis = 30000), rule().copy(cooldownSeconds = 30)).denied)
    }
    @Test fun cooldown_firstOpenIsAllowed() {
        assertFalse(engine.evaluate(context(), rule().copy(cooldownSeconds = 30)).denied)
    }
    @Test fun combinedRules_keepAllReasonsInPriorityOrder() {
        val result = engine.evaluate(context().copy(dailyUsageMillis = 60000, allowedOpens = 3, sinceLastCloseMillis = 0),
            rule().copy(dailyLimitSeconds = 60, maxOpenCount = 3, cooldownSeconds = 30))
        assertEquals(listOf(Reason.DAILY_USAGE, Reason.OPEN_COUNT, Reason.COOLDOWN), result.reasons)
        assertNull(result.retryAfterSeconds)
    }
    @Test fun allowlist_overridesRestrictions() {
        assertFalse(engine.evaluate(context().copy(safetyAllowlisted = true, allowedOpens = 10), rule().copy(maxOpenCount = 1)).denied)
    }
    @Test fun emergencyOverride_overridesRestrictions() {
        assertFalse(engine.evaluate(context().copy(emergencyOverride = true, allowedOpens = 10), rule().copy(maxOpenCount = 1)).denied)
    }
    @Test fun disabledRule_allows() {
        assertFalse(engine.evaluate(context().copy(allowedOpens = 10), rule().copy(maxOpenCount = 1, enabled = false)).denied)
    }
    @Test fun sessionLimit_blocksAtThreshold() {
        assertTrue(engine.evaluate(context().copy(entering = false, sessionUsageMillis = 7000), rule().copy(sessionLimitSeconds = 7)).denied)
    }
    @Test fun overnightSchedule_usesPreviousStartDay() {
        val schedule = Schedule(LocalTime.of(23, 0), LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY))
        fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
        assertTrue(schedule.contains(at("2026-10-05T23:00:00"), zone))
        assertTrue(schedule.contains(at("2026-10-06T06:59:59"), zone))
        assertFalse(schedule.contains(at("2026-10-06T07:00:00"), zone))
        assertFalse(schedule.contains(at("2026-10-06T23:00:00"), zone))
    }
    @Test fun daytimeSchedule_startInclusiveEndExclusive() {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val schedule = Schedule(local.toLocalTime(), local.toLocalTime().plusHours(1), setOf(local.dayOfWeek))
        assertTrue(schedule.contains(now, zone))
        assertFalse(schedule.contains(now + 3600000, zone))
    }
    @Test fun focusSession_blocksUntilDeadline() {
        assertTrue(engine.evaluate(context(), rule().copy(focusUntilEpochMillis = now + 1)).denied)
        assertFalse(engine.evaluate(context(), rule().copy(focusUntilEpochMillis = now)).denied)
    }
    @Test(expected = IllegalArgumentException::class) fun negativeDuration_isRejected() { rule().copy(dailyLimitSeconds = -1) }
    @Test(expected = IllegalArgumentException::class) fun zeroOpenLimit_isRejected() { rule().copy(maxOpenCount = 0) }
}
