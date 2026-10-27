package com.chronolock.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LockSettingTest {
    private val start = LocalDate.of(2026, 10, 1)

    @Test fun timeLock_endsByItself_goalLockHoldsUntilReleased() {
        assertTrue(LockSetting(untilEpochMillis = 100).lockedAt(99))
        assertFalse(LockSetting(untilEpochMillis = 100).lockedAt(100))
        assertTrue(LockSetting(goalDays = 3, goalStart = start).lockedAt(Long.MAX_VALUE))
        assertFalse(LockSetting().lockedAt(0))
    }

    @Test fun goalProgress_neverCountsDaysBeforeTheLockBegan() {
        val lock = LockSetting(goalDays = 3, goalStart = start)
        // A long streak from before the lock still counts only the days since it began.
        assertEquals(1, lock.goalProgress(keptStreak = 20, today = start.plusDays(1)))
        assertFalse(lock.goalReached(20, start.plusDays(2)))
        assertTrue(lock.goalReached(20, start.plusDays(3)))
        assertFalse(lock.goalReached(2, start.plusDays(10)))
    }

    @Test fun pieces_comeOnePerKeptDay() {
        val lock = LockSetting(goalDays = 5, goalStart = start, pieceCode = true, revealed = "4")
        assertTrue(lock.canReveal(2, start.plusDays(2)))
        assertFalse(lock.copy(revealed = "47").canReveal(2, start.plusDays(2)))
        assertFalse(lock.copy(pieceCode = false).canReveal(2, start.plusDays(2)))
    }
}

class ProProductTest {
    @Test fun proPass_entitlesOnlyWhenPurchased_andIsAcknowledgedOnce() {
        val bought = StorePurchase(listOf(ProProduct.ID), purchased = true, acknowledged = false, token = "t")
        assertTrue(ProProduct.entitled(listOf(bought)))
        assertFalse(ProProduct.entitled(listOf(bought.copy(purchased = false))))
        assertFalse(ProProduct.entitled(listOf(bought.copy(products = listOf(AdFreeProduct.ID)))))
        assertEquals(listOf(bought), ProProduct.toAcknowledge(listOf(bought, bought.copy(acknowledged = true, token = "u"))))
    }
}

class LockClockTest {
    private val zone = java.time.ZoneId.of("Asia/Seoul")
    @Test fun timeLock_holdsOnTheBootClock_whenTheWallClockJumps() {
        val lock = LockSetting(untilEpochMillis = 10_000, untilElapsedMillis = 2_000, bootId = 3)
        assertTrue(lock.lockedAt(ClockReading(50_000, 1_500, 3, zone)))   // wall clock moved past the end, same boot
        assertFalse(lock.lockedAt(ClockReading(50_000, 2_000, 3, zone)))  // boot clock reached the end too
        assertFalse(lock.lockedAt(ClockReading(50_000, 100, 4, zone)))    // after a reboot only the wall clock counts
        assertTrue(lock.lockedAt(ClockReading(9_000, 100, 4, zone)))
    }

    @Test fun shownLock_endsWhenTheBootClockLetsIt() {
        val lock = LockSetting(untilEpochMillis = 10_000, untilElapsedMillis = 2_000, bootId = 3)
        val shown = lock.shownAt(ClockReading(50_000, 1_500, 3, zone))
        assertTrue(shown.lockedAt(50_000))
        assertEquals(50_500L, shown.untilEpochMillis)
        assertEquals(lock, lock.shownAt(ClockReading(50_000, 2_000, 3, zone)))
        assertEquals(lock, lock.shownAt(ClockReading(50_000, 100, 4, zone)))
    }

    @Test fun goalDays_countCompletedDays_andDaysWithoutUse() {
        val start = java.time.LocalDate.of(2026, 10, 1)
        val rule = AppRule("a", "A", dailyLimitSeconds = 60)
        fun use(day: Long, millis: Long) = DailyKey("a", start.plusDays(day)) to DailyUsage(millis, 1)
        val daily = mapOf(use(0, 10_000), use(2, 10_000), use(3, 90_000), use(4, 10_000))
        // Day 1 had no use (kept); day 3 went over (stops the run); today (day 5) is not counted yet.
        assertEquals(1, goalKeptDays(daily, listOf(rule), start, start.plusDays(5)))
        assertEquals(3, goalKeptDays(daily, listOf(rule), start, start.plusDays(3)))
        assertEquals(0, goalKeptDays(daily, listOf(rule), start, start))
    }
}
