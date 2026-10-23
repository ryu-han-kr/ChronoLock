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

    @Test fun sessionLimit_remainsBlockedUntilAppIsLeft() {
        val rules = mapOf(app to AppRule(app, "Test", sessionLimitSeconds = 1))
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.tick(state, clock(1000), rules).state
        assertEquals(listOf(Reason.SESSION_USAGE), state.decision.reasons)
        state = monitor.tick(state, clock(2000), rules).state
        assertTrue("Session block must not disappear on the next tick", state.decision.denied)
        assertEquals(1000L, usage(state).usageMillis)
        assertEquals(1, usage(state).opens)
        state = monitor.foreground(state, null, clock(3000), rules).state
        state = monitor.foreground(state, app, clock(4000), rules).state
        assertFalse(state.decision.denied)
        assertEquals(2, usage(state).opens)
    }

    @Test fun restoreAfterReboot_doesNotReuseOldAllowedOpen() {
        val rules = rules(opens = 1)
        val before = monitor.foreground(MonitorState(), app, clock(), rules).state
        val restored = monitor.restore(before, clock(100, origin + 10000, 2), emptyMap(), app, rules).state
        assertEquals(listOf(Reason.OPEN_COUNT), restored.decision.reasons)
        assertNull(restored.active)
        assertEquals(1, usage(restored).opens)
    }

    @Test fun restoreSameBoot_preservesOpenButImmediatelyEnforcesRecoveredLimit() {
        val rules = rules(daily = 2, opens = 1)
        var before = monitor.foreground(MonitorState(), app, clock(), rules).state
        before = monitor.tick(before, clock(1000), rules).state
        val restored = monitor.restore(before, clock(2000), mapOf(app to 1000L), app, rules).state
        assertTrue("Recovered limit must be enforced before more usage is recorded", restored.decision.denied)
        assertEquals(2000L, usage(restored).usageMillis)
        assertEquals(1, usage(restored).opens)
    }

    @Test fun restore_doesNotRecoverDisabledApps() {
        val rules = mapOf(app to AppRule(app, "Test", dailyLimitSeconds = 1, enabled = false))
        val restored = monitor.restore(MonitorState(), clock(1000), mapOf(app to 1000L), app, rules).state
        assertTrue("Disabled apps must not gain usage records during recovery", restored.daily.isEmpty())
        assertNull(restored.active)
    }

    @Test fun sessionLimit_editingOrRemovingRuleReleasesBlock() {
        val rules = mapOf(app to AppRule(app, "Test", sessionLimitSeconds = 1))
        val entered = monitor.foreground(MonitorState(), app, clock(), rules).state
        val blocked = monitor.tick(entered, clock(1000), rules).state
        val relaxed = monitor.tick(blocked, clock(2000), mapOf(app to rules.getValue(app).copy(sessionLimitSeconds = 5))).state
        assertFalse(relaxed.decision.denied)
        assertNull(relaxed.blockedSession)
        val removed = monitor.tick(blocked, clock(2000), emptyMap()).state
        assertFalse(removed.decision.denied)
        assertNull(removed.blockedSession)
        assertNull(removed.active)
    }

    @Test fun sessionLimit_cooldownExpiryDoesNotRestartBlockedSession() {
        val rules = mapOf(app to AppRule(app, "Test", sessionLimitSeconds = 1, cooldownSeconds = 2))
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        state = monitor.tick(state, clock(1000), rules).state
        state = monitor.tick(state, clock(4000), rules).state
        assertEquals(listOf(Reason.SESSION_USAGE), state.decision.reasons)
        assertEquals(1, usage(state).blockedAttempts)
        state = monitor.foreground(state, null, clock(5000), rules).state
        state = monitor.foreground(state, app, clock(6000), rules).state
        assertFalse(state.decision.denied)
    }

    @Test fun restoreSameBoot_doesNotDoubleCountOpenOrDowntime() {
        val rules = rules(daily = 60, opens = 1)
        var before = monitor.foreground(MonitorState(), app, clock(), rules).state
        before = monitor.tick(before, clock(1000), rules).state
        val restored = monitor.restore(before, clock(10000), mapOf(app to 500L), app, rules).state
        assertFalse(restored.decision.denied)
        assertEquals(1500L, usage(restored).usageMillis)
        assertEquals(1, usage(restored).opens)
        assertEquals(2500L, usage(monitor.tick(restored, clock(11000), rules).state).usageMillis)
    }

    @Test fun restoreAfterReboot_enforcesPersistedCooldown() {
        val rules = rules(cooldown = 30)
        var before = monitor.foreground(MonitorState(), app, clock(), rules).state
        before = monitor.tick(before, clock(1000), rules).state
        val restored = monitor.restore(before, clock(100, origin + 10000, 2), emptyMap(), app, rules).state
        assertEquals(listOf(Reason.COOLDOWN), restored.decision.reasons)
        assertEquals(21L, restored.decision.retryAfterSeconds)
    }

    @Test fun restore_ignoresUnselectedAppsAndNegativeRecoveredUsage() {
        val rules = rules(daily = 60)
        var before = monitor.foreground(MonitorState(), app, clock(), rules).state
        before = monitor.tick(before, clock(1000), rules).state
        val restored = monitor.restore(before, clock(2000), mapOf(app to -500L, "other.app" to 2000L), app, rules).state
        assertEquals(1000L, usage(restored).usageMillis)
        assertEquals(1, restored.daily.size)
    }

    @Test fun restoreBlockedSession_keepsBlock_billsUseAfterOverlayDied_withoutDuplicatingAttempt() {
        val rules = mapOf(app to AppRule(app, "Test", sessionLimitSeconds = 1))
        val entered = monitor.foreground(MonitorState(), app, clock(), rules).state
        val blocked = monitor.tick(entered, clock(1000), rules).state
        // The service died after the block; the user kept using the app for 9 s without the overlay.
        val restored = monitor.restore(blocked, clock(10000), mapOf(app to 9000L), app, rules).state
        assertEquals(listOf(Reason.SESSION_USAGE), restored.decision.reasons)
        assertEquals(10000L, usage(restored).usageMillis)
        assertEquals(1, usage(restored).blockedAttempts)
        assertNull(restored.active)
    }

    @Test fun restoreActiveSession_countsRecoveredTimeTowardSessionLimit() {
        val rules = mapOf(app to AppRule(app, "Test", sessionLimitSeconds = 2))
        var before = monitor.foreground(MonitorState(), app, clock(), rules).state
        before = monitor.tick(before, clock(1000), rules).state
        val restored = monitor.restore(before, clock(2000), mapOf(app to 1000L), app, rules).state
        assertEquals(listOf(Reason.SESSION_USAGE), restored.decision.reasons)
        assertEquals(2000L, restored.blockedSession?.usageMillis)
    }

    @Test fun restoreBlockedSession_otherForegroundClearsOldSession() {
        val rules = mapOf(app to AppRule(app, "Test", sessionLimitSeconds = 1))
        val entered = monitor.foreground(MonitorState(), app, clock(), rules).state
        val blocked = monitor.tick(entered, clock(1000), rules).state
        val restored = monitor.restore(blocked, clock(2000), emptyMap(), "other.app", rules).state
        assertNull(restored.blockedSession)
        assertFalse(monitor.foreground(restored, app, clock(3000), rules).state.decision.denied)
    }

    @Test fun focusExpires_butDailyLimitStillApplies() {
        val rules = rules(daily = 1)
        var before = monitor.foreground(MonitorState(), app, clock(), rules).state
        before = monitor.tick(before, clock(1000), rules).state
        val focused = mapOf(app to rules.getValue(app).copy(focusUntilEpochMillis = origin + 5000))
        val during = monitor.tick(before, clock(2000), focused).state
        assertTrue(Reason.FOCUS in during.decision.reasons)
        val after = monitor.tick(during, clock(5000), focused).state
        assertEquals(listOf(Reason.DAILY_USAGE), after.decision.reasons)
    }

    @Test fun scheduleStartsAndEnds_whileAppRemainsForeground() {
        val start = Instant.ofEpochMilli(origin + 2000).atZone(zone)
        val rules = mapOf(app to AppRule(app, "Test", schedules = listOf(Schedule(start.toLocalTime(), start.toLocalTime().plusSeconds(2), setOf(start.dayOfWeek)))))
        var state = monitor.foreground(MonitorState(), app, clock(), rules).state
        assertFalse(state.decision.denied)
        state = monitor.tick(state, clock(2000), rules).state
        assertEquals(listOf(Reason.SCHEDULE), state.decision.reasons)
        state = monitor.tick(state, clock(4000), rules).state
        assertFalse(state.decision.denied)
        assertEquals(2000L, usage(state).usageMillis)
    }
}
