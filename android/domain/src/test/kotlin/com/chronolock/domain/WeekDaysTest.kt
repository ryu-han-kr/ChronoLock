package com.chronolock.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekDaysTest {
    @Test fun lastSevenDays_pairWithTheSameWeekdayAWeekEarlier_oldestFirst() {
        val today = LocalDate.of(2026, 10, 27)
        val totals = mapOf(today to 5L, today.minusDays(7) to 3L, today.minusDays(6) to 2L, today.minusDays(13) to 1L)
        val days = weekDays(totals, today)
        assertEquals(7, days.size)
        assertEquals(2L to 1L, days.first())
        assertEquals(5L to 3L, days.last())
        assertEquals(0L to 0L, days[3])
    }
}
