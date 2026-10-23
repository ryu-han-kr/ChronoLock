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
