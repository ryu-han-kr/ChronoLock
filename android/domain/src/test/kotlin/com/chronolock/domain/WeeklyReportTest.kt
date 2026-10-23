package com.chronolock.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class WeeklyReportTest {
    private val sunday = LocalDate.of(2026, 10, 25)

    @Test fun weekTotals_compareTheLastSevenDaysWithTheSevenBefore() {
        val days = (0L..13L).associate { sunday.minusDays(it) to if (it < 7) 1_000L else 2_000L }
        val totals = weekTotals(days, sunday)
        assertEquals(7_000L, totals.thisWeekMillis)
        assertEquals(14_000L, totals.lastWeekMillis)
        assertEquals(-50, totals.changePercent)
        assertNull(weekTotals(mapOf(sunday to 5L), sunday).changePercent)
    }

    @Test fun report_isDueFromSundayEveningUntilWednesday_onceAWeek() {
        val monday = sunday.minusDays(6)
        assertEquals(monday, weeklyReportDue(sunday.atTime(18, 0), null))
        assertNull(weeklyReportDue(sunday.atTime(17, 59), null))
        assertEquals(monday, weeklyReportDue(sunday.plusDays(1).atTime(11, 0), null))
        assertEquals(monday, weeklyReportDue(sunday.plusDays(3).atTime(23, 0), null))
        assertNull(weeklyReportDue(sunday.plusDays(4).atTime(9, 0), null))
        assertNull(weeklyReportDue(sunday.atTime(21, 0), monday))
        assertTrue(SitePreset.entries.all { preset -> preset.domains.all { SiteDomains.valid(it) } })
    }
}
