package com.chronolock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeBankTest {
    private val minute = 60_000L

    @Test fun onlyAFinishedLongEnoughFocus_earnsOnce() {
        val start = 1_000_000L
        val until = start + 25 * minute
        assertEquals(0, TimeBank.earned(until, start, null, until - 1))
        assertEquals(10, TimeBank.earned(until, start, null, until))
        assertEquals(0, TimeBank.earned(until, start, credited = until, nowEpochMillis = until + minute))
        assertEquals(0, TimeBank.earned(start + 24 * minute, start, null, until))
        // Stopped early leaves no end; a start older than the kept events is unknown.
        assertEquals(0, TimeBank.earned(null, start, null, until))
        assertEquals(0, TimeBank.earned(until, null, null, until))
    }

    @Test fun bank_isCappedAndNeverNegative() {
        assertEquals(30, TimeBank.add(25, 10))
        assertEquals(0, TimeBank.add(5, -10))
        assertEquals(20, TimeBank.add(10, 10))
    }
}
