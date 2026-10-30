package com.chronolock.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeTest {
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test fun asksOncePerNight_fromNineUntilThree() {
        assertFalse(Bedtime.promptDue(at(30, 20, 59), null))
        assertTrue(Bedtime.promptDue(at(30, 21), null))
        assertTrue(Bedtime.promptDue(at(31, 2, 59), null))
        assertFalse(Bedtime.promptDue(at(31, 3), null))
        // After midnight belongs to the evening before, so it is not asked twice.
        assertFalse(Bedtime.promptDue(at(31, 1), LocalDate.of(2026, 10, 30)))
        assertTrue(Bedtime.promptDue(at(31, 22), LocalDate.of(2026, 10, 30)))
    }

    @Test fun focusRunsToTheNextSevenOClock() {
        assertEquals(at(31, 7), Bedtime.morning(at(30, 23)))
        assertEquals(at(31, 7), Bedtime.morning(at(31, 1)))
        assertEquals(at(31, 7), Bedtime.morning(at(30, 21)))
    }
}
