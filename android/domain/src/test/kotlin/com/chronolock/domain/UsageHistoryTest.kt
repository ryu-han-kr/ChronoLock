package com.chronolock.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UsageHistoryTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val origin = Instant.parse("2026-10-15T00:00:00Z").toEpochMilli()
    private fun e(ms: Long, type: HistoryEventType, app: String = "app.a", activity: String = "Main") =
        HistoryEvent(origin + ms, type, app, activity)
    private val resume = HistoryEventType.RESUME
    private val pause = HistoryEventType.PAUSE
    private fun sum(events: List<HistoryEvent>, from: Long = origin, until: Long = origin + 10_000) =
        aggregateHistory(events, from, until, zone)

    @Test fun unrestrictedApps_haveIndependentTimeAndOpens() {
        val rows = sum(listOf(e(0, resume), e(2000, resume, "app.b"), e(5000, resume), e(7000, pause)))
        assertEquals(HistoryUsage(4000, 2), rows[DailyKey("app.a", LocalDate.of(2026, 10, 15))])
        assertEquals(HistoryUsage(3000, 1), rows[DailyKey("app.b", LocalDate.of(2026, 10, 15))])
    }
    @Test fun sameAppActivityChanges_andDuplicateResume_doNotAddOpens() {
        val rows = sum(listOf(e(0, resume), e(1000, resume), e(2000, pause), e(2200, resume, activity = "Detail"),
            e(2300, pause), e(4000, pause, activity = "Detail")))
        assertEquals(HistoryUsage(3800, 1), rows.values.single())
    }
    @Test fun leavingAndReturning_countsAnotherVisit() {
        assertEquals(HistoryUsage(2000, 2), sum(listOf(e(0, resume), e(1000, pause), e(4000, resume), e(5000, pause))).values.single())
    }
    @Test fun homeTransition_countsReturn_evenIfFast() {
        val rows = sum(listOf(e(0, resume), e(1000, resume, "home"), e(1100, resume), e(2100, pause)))
        assertEquals(HistoryUsage(2000, 2), rows[DailyKey("app.a", LocalDate.of(2026, 10, 15))])
    }
    @Test fun lockAndShutdown_endSessions() {
        val rows = sum(listOf(e(0, resume), e(1000, HistoryEventType.SCREEN_OFF), e(1100, resume), e(2100, HistoryEventType.SHUTDOWN)))
        assertEquals(HistoryUsage(2000, 2), rows.values.single())
    }
    @Test fun rebootWithMissingShutdown_doesNotCountPoweredOffTime() {
        val rows = sum(listOf(e(0, resume), e(8000, HistoryEventType.STARTUP), e(9000, resume)))
        assertEquals(1000L, rows.values.single().millis)
    }
    @Test fun midnight_splitsTime_andOnlyCountsOpenOnStartingDay() {
        val start = Instant.parse("2026-10-15T14:59:00Z").toEpochMilli()
        val rows = aggregateHistory(listOf(HistoryEvent(start, resume, "app.a")), start, start + 120_000, zone)
        assertEquals(HistoryUsage(60_000, 1), rows[DailyKey("app.a", LocalDate.of(2026, 10, 15))])
        assertEquals(HistoryUsage(60_000, 0), rows[DailyKey("app.a", LocalDate.of(2026, 10, 16))])
    }
    @Test fun rangeBoundary_clipsTime_andExcludesEarlierOrFutureOpens() {
        val rows = sum(listOf(e(0, resume), e(5000, pause), e(10_000, resume)), from = origin + 2000)
        assertEquals(HistoryUsage(3000, 0), rows.values.single())
    }
    @Test fun daylightSavingDay_usesRealMidnightBoundary() {
        val ny = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 11, 1)
        val start = day.atStartOfDay(ny).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(ny).toInstant().toEpochMilli()
        val rows = aggregateHistory(listOf(HistoryEvent(start, resume, "app.a")), start, end, ny)
        assertEquals(HistoryUsage(25 * 3600_000L, 1), rows.values.single())
    }
    @Test fun noEvents_isEmpty_notSyntheticUsage() { assertTrue(sum(emptyList()).isEmpty()) }
}
