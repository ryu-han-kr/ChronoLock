package com.chronolock.domain

import org.junit.Assert.*
import org.junit.Test

class FrictionTest {
    @Test fun pause_growsWithEachOpenToday_upToThirtySeconds() {
        assertEquals(5, pauseDelaySeconds(5, 0))
        assertEquals(9, pauseDelaySeconds(5, 2))
        assertEquals(30, pauseDelaySeconds(10, 40))
    }

    @Test fun aPauseOrCheckInAlone_isAValidRule_andRoundTrips() {
        val rule = RuleDraft(daily = "", pauseSeconds = 5, checkInMinutes = 10).build("a", "A")
        assertEquals(5, rule.pauseSeconds)
        assertEquals(10, rule.checkInMinutes)
        assertEquals(RuleDraft(daily = "", pauseSeconds = 5, checkInMinutes = 10, schedules = listOf(ScheduleDraft())), RuleDraft.from(rule))
    }

    @Test fun shorterPauseOrRarerCheckIn_isLoosening() {
        val rule = AppRule("a", "A", pauseSeconds = 5, checkInMinutes = 10)
        assertTrue(Strictness.loosens(rule, rule.copy(pauseSeconds = null), 0))
        assertTrue(Strictness.loosens(rule, rule.copy(pauseSeconds = 3), 0))
        assertTrue(Strictness.loosens(rule, rule.copy(checkInMinutes = 20), 0))
        assertFalse(Strictness.loosens(rule, rule.copy(pauseSeconds = 10, checkInMinutes = 5), 0))
    }
}
