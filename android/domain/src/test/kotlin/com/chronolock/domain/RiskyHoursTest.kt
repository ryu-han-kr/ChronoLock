package com.chronolock.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiskyHoursTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun visit(app: String, day: Int, fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int, nextDay: Boolean = false) = listOf(
        HistoryEvent(at(day, fromHour, fromMinute), HistoryEventType.RESUME, app),
        HistoryEvent(at(if (nextDay) day + 1 else day, toHour, toMinute), HistoryEventType.PAUSE, app))

    @Test fun usage_isSplitAtEachHour_andAcrossMidnight() {
        val events = visit("video", 20, 23, 30, 0, 45, nextDay = true)
        val hourly = hourlyUsage(events, at(20, 0), at(22, 0), zone)
        assertEquals(30 * 60_000L, hourly["video" to 23])
        assertEquals(45 * 60_000L, hourly["video" to 0])
    }

    @Test fun lateNightVideo_isTheRiskyWindow() {
        val events = (20..26).flatMap { day -> visit("video", day, 23, 0, 0, 50, nextDay = true) } +
            (20..26).flatMap { day -> visit("chat", day, 12, 0, 12, 20) } +
            listOf(HistoryEvent(at(20, 13), HistoryEventType.SCREEN_OFF))
        val window = riskyWindow(hourlyUsage(events, at(20, 0), at(28, 0), zone), setOf("video", "chat"))!!
        assertEquals("video", window.app)
        assertEquals(23, window.startHour)
        assertEquals(1, window.endHour)
        assertEquals(ScheduleDraft("23:00", "01:00", ScheduleDraft.EVERY_DAY), window.scheduleDraft())
    }

    @Test fun littleUse_suggestsNothing_andOtherAppsAreIgnored() {
        val events = visit("video", 20, 22, 0, 22, 30)
        val hourly = hourlyUsage(events, at(20, 0), at(21, 0), zone)
        assertNull(riskyWindow(hourly, setOf("video")))
        assertNull(riskyWindow(hourly, setOf("chat"), minimumMillis = 1))
    }
}
