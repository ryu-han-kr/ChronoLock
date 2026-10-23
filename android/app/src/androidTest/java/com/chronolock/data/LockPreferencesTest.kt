package com.chronolock.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Passwords pause after five wrong tries, can be changed with the current one, and piece codes unlock goal locks. */
class LockPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = Preferences(context)
    private val now = 1_800_000_000_000L

    @After fun clean() = runBlocking {
        // Leave no lock behind for other tests: wait out any block and release with the right code.
        val lock = preferences.lock.first()
        if (lock.goalLocked) preferences.endGoalLock(if (lock.pieceCode) codeOf(lock) else "2468", now + 3_600_000)
    }
    private suspend fun codeOf(lock: com.chronolock.domain.LockSetting): String {
        while (preferences.lock.first().revealed.length < lock.goalDays!!) preferences.revealNextPiece()
        return preferences.lock.first().revealed
    }

    @Test fun fiveWrongTries_pauseForTenMinutes_andARightOneResetsTheCount() = runBlocking {
        preferences.setHistoryPin("1357")
        repeat(4) { assertEquals(4 - it, preferences.checkHistoryPin("0000", now).triesLeft) }
        assertTrue(preferences.checkHistoryPin("1357", now).ok)
        repeat(4) { preferences.checkHistoryPin("0000", now) }
        val blocked = preferences.checkHistoryPin("0000", now)
        assertEquals(now + 600_000, blocked.blockedUntil)
        // Even the right password is refused while paused.
        assertFalse(preferences.checkHistoryPin("1357", now + 599_999).ok)
        assertTrue(preferences.checkHistoryPin("1357", now + 600_000).ok)
    }

    @Test fun passwordChanges_needTheCurrentOne() = runBlocking {
        preferences.setHistoryPin("1357")
        assertFalse(preferences.changeHistoryPin("0000", "2468", now).ok)
        assertTrue(preferences.changeHistoryPin("1357", "2468", now).ok)
        assertTrue(preferences.checkHistoryPin("2468", now).ok)
    }

    @Test fun pieceCode_isHandedOutDigitByDigit_andUnlocksTheGoal() = runBlocking {
        preferences.startGoalLock(3, LocalDate.of(2026, 10, 1), pin = null)
        val lock = preferences.lock.first()
        assertTrue(lock.pieceCode && lock.goalLocked && lock.revealed.isEmpty())
        preferences.revealNextPiece()
        assertEquals(1, preferences.lock.first().revealed.length)
        val code = codeOf(lock)
        assertEquals(3, code.length)
        assertTrue(preferences.endGoalLock(code, now).ok)
        assertFalse(preferences.lock.first().goalLocked)
    }

    @Test fun ownGoalPassword_canBeChanged_andThenUnlocks() = runBlocking {
        preferences.startGoalLock(3, LocalDate.of(2026, 10, 1), pin = "1111")
        assertTrue(preferences.changeGoalPin("1111", "2468", now).ok)
        assertFalse(preferences.endGoalLock("1111", now).ok)
        assertTrue(preferences.endGoalLock("2468", now).ok)
    }
}
