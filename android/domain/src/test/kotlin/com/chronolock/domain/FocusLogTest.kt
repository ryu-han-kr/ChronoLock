package com.chronolock.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusLogTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun runs_roundTrip_onceEach_andOldOnesGo() {
        val finished = FocusRun(at(30, 9), at(30, 10), true)
        var runs = FocusLog.add(emptyList(), finished, at(30, 10))
        runs = FocusLog.add(runs, finished, at(30, 11))
        runs = FocusLog.add(runs, FocusRun(at(30, 13), at(30, 13, 10), false), at(30, 14))
        assertEquals(2, runs.size)
        assertEquals(runs, FocusLog.decode(FocusLog.encode(runs)))
        assertEquals(listOf(finished), FocusLog.decode(FocusLog.encode(listOf(finished)) + ";junk;5:3:1"))
        val later = at(30, 14) + FocusLog.KEEP_DAYS * 86_400_000L
        assertEquals(1, FocusLog.add(runs, FocusRun(later, later + 1, true), later).size)
    }

    @Test fun week_countsTimeFinishedRunsAndDays() {
        val runs = listOf(
            FocusRun(at(30, 9), at(30, 10), true),
            FocusRun(at(30, 20), at(30, 20, 15), false),
            FocusRun(at(26, 22), at(27, 1), true),
            FocusRun(at(20, 9), at(20, 10), true),
        )
        val week = FocusLog.week(runs, LocalDate.of(2026, 10, 30), zone)
        assertEquals((60 + 15 + 180) * 60_000L, week.millis)
        assertEquals(2, week.finished)
        assertEquals(2, week.days)
    }
}
