package com.chronolock.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class StrictnessTest {
    private val now = 1_000_000L
    private val sleep = Schedule(LocalTime.of(23, 0), LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY))
    private val base = AppRule("app", "App", dailyLimitSeconds = 600, maxOpenCount = 5, cooldownSeconds = 60, sessionLimitSeconds = 120,
        schedules = listOf(sleep))

    @Test fun tighteningOrUnchanged_appliesAtOnce() {
        assertFalse(Strictness.loosens(base, base, now))
        assertFalse(Strictness.loosens(base, base.copy(dailyLimitSeconds = 300, maxOpenCount = 3, cooldownSeconds = 120), now))
        assertFalse(Strictness.loosens(base, base.copy(schedules = listOf(sleep, Schedule(LocalTime.NOON, LocalTime.of(13, 0), setOf(DayOfWeek.FRIDAY)))), now))
        assertFalse(Strictness.loosens(null, base, now))
        assertFalse(Strictness.loosens(base.copy(enabled = false), base.copy(enabled = false, dailyLimitSeconds = null), now))
    }

    @Test fun anyRelaxation_waits() {
        assertTrue(Strictness.loosens(base, null, now))
        assertTrue(Strictness.loosens(base, base.copy(enabled = false), now))
        assertTrue(Strictness.loosens(base, base.copy(dailyLimitSeconds = 900), now))
        assertTrue(Strictness.loosens(base, base.copy(sessionLimitSeconds = null), now))
        assertTrue(Strictness.loosens(base, base.copy(maxOpenCount = 6), now))
        assertTrue(Strictness.loosens(base, base.copy(cooldownSeconds = 30), now))
        assertTrue(Strictness.loosens(base, base.copy(schedules = emptyList()), now))
        // Mixed edits wait as a whole: a tighter daily limit does not buy a removed schedule.
        assertTrue(Strictness.loosens(base, base.copy(dailyLimitSeconds = 60, schedules = emptyList()), now))
    }

    @Test fun pausing_waitsButEndingAPauseDoesNot() {
        assertTrue(Strictness.loosens(base, base.copy(pausedUntilEpochMillis = now + 60_000), now))
        val paused = base.copy(pausedUntilEpochMillis = now + 60_000)
        assertFalse(Strictness.loosens(paused, base, now))
        assertTrue(Strictness.loosens(paused, base.copy(pausedUntilEpochMillis = now + 120_000), now))
        // An expired pause does not count as already relaxed.
        assertTrue(Strictness.loosens(base.copy(pausedUntilEpochMillis = now - 1), base.copy(pausedUntilEpochMillis = now + 1), now))
    }

    @Test fun pausedRule_allowsUntilItsEnd() {
        val engine = RuleEngine()
        val zone = ZoneId.of("Asia/Seoul")
        val rule = base.copy(maxOpenCount = 1, pausedUntilEpochMillis = now + 1000)
        assertFalse(engine.evaluate(RuleContext(now, zone, allowedOpens = 3), rule).denied)
        assertTrue(engine.evaluate(RuleContext(now + 1000, zone, allowedOpens = 3), rule).denied)
    }

    @Test fun pauseEnd_minutesOrNextMidnight() {
        val zone = ZoneId.of("Asia/Seoul")
        val start = LocalDateTime.parse("2026-10-22T21:15:00").atZone(zone).toInstant().toEpochMilli()
        assertEquals(start + 600_000, Strictness.pauseEnd(start, 10, zone))
        assertEquals(LocalDateTime.parse("2026-10-23T00:00:00").atZone(zone).toInstant().toEpochMilli(), Strictness.pauseEnd(start, null, zone))
    }

    @Test fun loweringDelay_waits() {
        assertTrue(Strictness.loosensDelay(30, 0))
        assertTrue(Strictness.loosensDelay(30, 10))
        assertFalse(Strictness.loosensDelay(10, 60))
        assertFalse(Strictness.loosensDelay(0, 10))
    }

    @Test fun strictSetting_raisesAtOnceAndLowersAfterTheDelay() {
        val on = StrictSetting().request(30, now)
        assertEquals(StrictSetting(30), on)
        val off = on.request(0, now)
        assertEquals(30, off.delayAt(now))
        assertEquals(0, off.waitingAt(now))
        assertEquals(30, off.delayAt(now + 30 * 60_000L - 1))
        assertEquals(0, off.delayAt(now + 30 * 60_000L))
        assertNull(off.waitingAt(now + 30 * 60_000L))
        assertEquals(StrictSetting(30), off.cancel(now + 1))
        assertEquals(StrictSetting(60), off.request(60, now + 1))
        // Once the decrease has taken over, turning strict mode back on starts from it and applies at once.
        assertEquals(StrictSetting(10), off.request(10, now + 31 * 60_000L))
    }

    @Test fun dayLongWait_appliesAtOnce_andLoweringItWaitsADay() {
        val day = StrictSetting().request(1440, 0)
        assertEquals(1440, day.delayAt(0))
        val lowered = day.request(60, 1_000)
        assertEquals(1440, lowered.delayAt(1_000 + 1439 * 60_000L))
        assertEquals(60, lowered.delayAt(1_000 + 1440 * 60_000L))
    }

    @Test fun loweringTheDelay_waitsOnTheBootClockToo() {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val start = ClockReading(1_000, 5_000, 3, zone)
        val lowered = StrictSetting(60).request(0, start)
        // The wall clock moved a day ahead after 10 minutes: the hour still holds within this boot.
        val jumped = ClockReading(1_000 + 86_400_000L, 5_000 + 10 * 60_000L, 3, zone)
        assertEquals(0, lowered.delayAt(jumped.epochMillis))
        assertEquals(60, lowered.delayAt(jumped))
        assertEquals(StrictSetting(60), lowered.cancel(jumped))
        assertEquals(0, lowered.delayAt(ClockReading(1_000 + 86_400_000L, 5_000 + 60 * 60_000L, 3, zone)))
        // After a reboot only the wall clock can tell.
        assertEquals(0, lowered.delayAt(ClockReading(1_000 + 86_400_000L, 1_000, 4, zone)))
        // Moving the wall clock back keeps it waiting, as a lock does.
        assertEquals(60, lowered.delayAt(ClockReading(0, 5_000 + 61 * 60_000L, 3, zone)))
    }
}
