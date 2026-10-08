package com.chronolock.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class UsageMonitorTest {
    private val monitor = UsageMonitor()
    private val app = "test.app"
    private val zone = ZoneId.of("Asia/Seoul")
    private val origin = Instant.parse("2026-10-08T09:00:00Z").toEpochMilli()
    private fun clock(ms: Long = 0, epoch: Long = origin + ms, boot: Int = 1) = ClockReading(epoch, ms, boot, zone)
    private fun rules(daily: Long? = null, opens: Int? = null, cooldown: Long? = null) =
        mapOf(app to AppRule(app, "Test", daily, opens, cooldown))
    private fun usage(state: MonitorState, date: LocalDate = clock().date) = state.daily[DailyKey(app, date)] ?: DailyUsage()

    @Test fun continuousUse_blocksWithoutAnotherForegroundEvent() {
        val rules = rules(daily = 60)
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.tick(state, clock(59999), rules).state
        assertFalse(state.decision.denied)
        state = monitor.tick(state, clock(60000), rules).state
        assertTrue(state.decision.denied)
        assertEquals(60000L, usage(state).usageMillis)
        assertEquals(1, usage(state).blockedAttempts)
        state = monitor.tick(state, clock(120000), rules).state
        assertEquals(60000L, usage(state).usageMillis)
        assertEquals(1, usage(state).blockedAttempts)
    }

    @Test fun fourthOpen_isBlockedAndDoesNotConsumeAllowedOpen() {
        val rules = rules(opens = 3)
        var state = MonitorState()
        repeat(3) { index ->
            state = monitor.foreground(state, app, clock(index * 2000L), rules).state
            assertFalse(state.decision.denied)
            state = monitor.foreground(state, null, clock(index * 2000L + 1000), rules).state
        }
        state = monitor.foreground(state, app, clock(6000), rules).state
        assertEquals(listOf(Reason.OPEN_COUNT), state.decision.reasons)
        assertEquals(3, usage(state).opens)
        assertEquals(3000L, usage(state).usageMillis)
    }

    @Test fun duplicateActivityEvents_doNotCountAsNewOpens() {
        val rules = rules(opens = 3)
        var state = MonitorState()
        repeat(20) { state = monitor.foreground(state, app, clock(it * 100L), rules).state }
        assertEquals(1, usage(state).opens)
        assertFalse(state.decision.denied)
        assertEquals(1900L, usage(state).usageMillis)
    }

    @Test fun blockedAttempts_doNotExtendCooldown() {
        val rules = rules(cooldown = 30)
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.foreground(state, null, clock(1000), rules).state
        state = monitor.foreground(state, app, clock(10000), rules).state
        assertTrue(state.decision.denied)
        state = monitor.foreground(state, null, clock(11000), rules).state
        state = monitor.foreground(state, app, clock(30000), rules).state
        assertTrue(state.decision.denied)
        assertEquals(1000L, state.lastClosed[app]?.elapsedMillis)
        state = monitor.tick(state, clock(31000), rules).state
        assertFalse(state.decision.denied)
        assertEquals(2, usage(state).opens)
        assertEquals(2, usage(state).blockedAttempts)
    }

    @Test fun fractionalTicks_doNotLoseUsage() {
        val rules = rules(daily = 1)
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        repeat(10) { state = monitor.tick(state, clock((it + 1) * 100L), rules).state }
        assertEquals(1000L, usage(state).usageMillis)
        assertTrue(state.decision.denied)
    }

    @Test fun midnight_splitsUsageAndResetsOpenBucket() {
        val rules = rules(daily = 60, opens = 3)
        val midnight = LocalDate.parse("2026-10-09").atStartOfDay(zone).toInstant().toEpochMilli()
        var state = monitor.foreground(MonitorState(), app, clock(epoch = midnight - 500), rules).state
        state = monitor.tick(state, clock(1500, midnight + 1000), rules).state
        assertEquals(500L, usage(state, LocalDate.parse("2026-10-08")).usageMillis)
        assertEquals(1000L, usage(state, LocalDate.parse("2026-10-09")).usageMillis)
        assertEquals(0, usage(state, LocalDate.parse("2026-10-09")).opens)
    }

    @Test fun dailyBlock_expiresAtMidnight() {
        val midnight = LocalDate.parse("2026-10-09").atStartOfDay(zone).toInstant().toEpochMilli()
        val rules = rules(daily = 1)
        var state = monitor.foreground(MonitorState(), app, clock(epoch = midnight - 2000), rules).state
        state = monitor.tick(state, clock(1000, midnight - 1000), rules).state
        assertTrue(state.decision.denied)
        state = monitor.tick(state, clock(2000, midnight), rules).state
        assertFalse(state.decision.denied)
    }

    @Test fun backwardWallClock_doesNotSubtractUsageOrExtendLiveCooldown() {
        val rules = rules(cooldown = 30)
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.foreground(state, null, clock(1000, origin - 3600000), rules).state
        assertEquals(1000L, usage(state).usageMillis)
        state = monitor.foreground(state, app, clock(31000, origin - 3599000), rules).state
        assertFalse(state.decision.denied)
    }

    @Test fun rebootCooldown_usesPersistedWallDeadline() {
        val rules = rules(cooldown = 30)
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.foreground(state, null, clock(1000), rules).state
        state = monitor.foreground(state, app, clock(100, origin + 20000, 2), rules).state
        assertTrue(state.decision.denied)
        state = monitor.tick(state, clock(11100, origin + 31000, 2), rules).state
        assertFalse(state.decision.denied)
    }

    @Test fun reconnect_doesNotBillUnobservedTime() {
        val rules = rules(daily = 60)
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.tick(state, clock(1000), rules).state
        state = monitor.reconnect(state, clock(600000)).state
        assertNull(state.active)
        assertEquals(1000L, usage(state).usageMillis)
        assertEquals(1000L, state.lastClosed[app]?.epochMillis?.minus(origin))
        assertEquals(1, usage(state).opens)
    }

    @Test fun deletingRule_unblocksForegroundApp() {
        var state = monitor.foreground(MonitorState(), app, clock(), rules(daily = 1)).state
        state = monitor.tick(state, clock(1000), rules(daily = 1)).state
        assertTrue(state.decision.denied)
        state = monitor.tick(state, clock(2000), emptyMap()).state
        assertFalse(state.decision.denied)
        assertNull(state.active)
    }

    @Test fun unselectedApps_areNotRecorded() {
        var state = monitor.foreground(MonitorState(), "other.app", clock(), rules(daily = 60)).state
        state = monitor.tick(state, clock(60000), rules(daily = 60)).state
        assertTrue(state.daily.isEmpty())
        assertNull(state.active)
    }

    @Test fun switchingSelectedApps_closesPreviousAndMetersOnlyCurrent() {
        val rules = rules(daily = 60) + ("second.app" to AppRule("second.app", "Second", dailyLimitSeconds = 60))
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.foreground(state, "second.app", clock(1500), rules).state
        state = monitor.tick(state, clock(3000), rules).state
        assertEquals(1500L, usage(state).usageMillis)
        assertEquals(1500L, state.daily[DailyKey("second.app", clock().date)]?.usageMillis)
    }

    @Test fun daylightSavingMidnight_usesCalendarBoundary() {
        val dst = ZoneId.of("America/New_York")
        val start = LocalDate.parse("2026-11-01").atStartOfDay(dst).toInstant().toEpochMilli()
        val end = LocalDate.parse("2026-11-02").atStartOfDay(dst).toInstant().toEpochMilli()
        val rules = rules(opens = 3)
        var state = monitor.foreground(MonitorState(), app, ClockReading(start, 0, 1, dst), rules).state
        state = monitor.tick(state, ClockReading(end, end - start, 1, dst), rules).state
        assertEquals(25L * 3600000, usage(state, LocalDate.parse("2026-11-01")).usageMillis)
    }
}
