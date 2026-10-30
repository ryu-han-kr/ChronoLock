package com.chronolock.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePolicyTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val study = Schedule(LocalTime.of(9, 0), LocalTime.of(18, 0), DayOfWeek.values().toSet())
    private fun clock(hour: Int, minute: Int = 0, elapsed: Long = 0) =
        ClockReading(LocalDateTime.of(2026, 10, 30, hour, minute).atZone(zone).toInstant().toEpochMilli(), elapsed, 1, zone)

    @Test fun duringAWindow_onlyAppsOutsideThePolicyOpen() {
        val policy = PhonePolicy(windows = listOf(study), apps = setOf("game", "video"))
        assertEquals(listOf(Reason.ALLOWED_ONLY), policy.reasons("game", clock(10), emptyMap()))
        assertEquals(emptyList<Reason>(), policy.reasons("game", clock(19), emptyMap()))
        // An always-allowed app (dictionary) is simply not among the covered apps.
        assertEquals(emptyList<Reason>(), policy.reasons("dictionary", clock(10), emptyMap()))
    }

    @Test fun budget_countsCoveredAppsTogether() {
        val policy = PhonePolicy(dailyBudgetSeconds = 3600, apps = setOf("game", "video"))
        val now = clock(20)
        val half = mapOf(DailyKey("game", now.date) to DailyUsage(30 * 60_000L), DailyKey("dictionary", now.date) to DailyUsage(90 * 60_000L))
        assertEquals(emptyList<Reason>(), policy.reasons("video", now, half))
        val full = half + (DailyKey("video", now.date) to DailyUsage(30 * 60_000L))
        assertEquals(listOf(Reason.PHONE_BUDGET), policy.reasons("video", now, full))
        assertEquals(60 * 60_000L, policy.usedToday(full, now))
    }

    @Test fun monitor_watchesAppsWithoutARule_andBlocksAtTheBudget() {
        val monitor = UsageMonitor()
        val policy = PhonePolicy(dailyBudgetSeconds = 1800, apps = setOf("game"))
        var state = monitor.foreground(MonitorState(), "game", clock(20, 0, 0), emptyMap(), policy).state
        assertEquals(1, state.daily[DailyKey("game", clock(20).date)]?.opens)
        state = monitor.tick(state, clock(20, 29, 29 * 60_000L), emptyMap(), policy).state
        assertFalse(state.decision.denied)
        state = monitor.tick(state, clock(20, 30, 30 * 60_000L), emptyMap(), policy).state
        assertTrue(state.decision.denied)
        assertEquals(listOf(Reason.PHONE_BUDGET), state.decision.reasons)
        // Without the policy the same app is not watched at all.
        assertEquals(null, monitor.foreground(MonitorState(), "game", clock(20), emptyMap()).state.active)
    }

    @Test fun loosening_isDroppingAWindowOrEasingTheBudget() {
        val policy = PhonePolicy(windows = listOf(study), dailyBudgetSeconds = 3600)
        assertTrue(policy.loosenedBy(policy.copy(windows = emptyList())))
        assertTrue(policy.loosenedBy(policy.copy(dailyBudgetSeconds = 7200)))
        assertTrue(policy.loosenedBy(policy.copy(dailyBudgetSeconds = null)))
        assertFalse(policy.loosenedBy(policy.copy(dailyBudgetSeconds = 1800)))
        assertFalse(PhonePolicy.NONE.loosenedBy(policy))
    }
}
