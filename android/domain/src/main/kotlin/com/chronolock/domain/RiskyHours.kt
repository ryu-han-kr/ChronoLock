package com.chronolock.domain

import java.time.Instant
import java.time.ZoneId

/** The [hours]-long stretch of the day, from [startHour], when [app] took the most time over the recorded days. */
data class RiskyWindow(val app: String, val startHour: Int, val hours: Int, val millis: Long) {
    val endHour: Int get() = (startHour + hours) % 24

    /** A daily schedule blocking this stretch, ready for the rule editor. */
    fun scheduleDraft() = ScheduleDraft("%02d:00".format(startHour), "%02d:00".format(endHour), ScheduleDraft.EVERY_DAY)
}

/** Foreground time per app and hour of the day (0..23) between [from] and [until], from raw history events. */
fun hourlyUsage(events: List<HistoryEvent>, from: Long, until: Long, zone: ZoneId): Map<Pair<String, Int>, Long> {
    val result = mutableMapOf<Pair<String, Int>, Long>()
    var active: HistoryEvent? = null
    fun close(at: Long) {
        val current = active ?: return
        var cursor = maxOf(current.at, from)
        val end = minOf(at, until)
        while (cursor < end) {
            val time = Instant.ofEpochMilli(cursor).atZone(zone)
            val boundary = minOf(end, time.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli())
            val key = current.app to time.hour
            result[key] = (result[key] ?: 0L) + boundary - cursor
            cursor = boundary
        }
        active = null
    }
    events.sortedBy { it.at }.forEach { event ->
        if (event.at >= until) return@forEach
        when (event.type) {
            HistoryEventType.RESUME -> { close(event.at); if (event.app.isNotBlank()) active = event }
            HistoryEventType.PAUSE -> if (active?.app == event.app) close(event.at)
            HistoryEventType.SCREEN_OFF, HistoryEventType.SHUTDOWN, HistoryEventType.STARTUP -> close(event.at)
        }
    }
    close(until)
    return result
}

/**
 * The window of [hours] (wrapping past midnight) holding the most time for any one of [apps], when it reaches
 * [minimumMillis] in total; null when nothing stands out enough to suggest blocking.
 */
fun riskyWindow(hourly: Map<Pair<String, Int>, Long>, apps: Set<String>, hours: Int = 2,
    minimumMillis: Long = 60 * 60_000L): RiskyWindow? {
    require(hours in 1..23)
    return apps.flatMap { app ->
        (0 until 24).map { start -> RiskyWindow(app, start, hours, (0 until hours).sumOf { hourly[app to (start + it) % 24] ?: 0L }) }
    }.filter { it.millis >= minimumMillis }
        .maxWithOrNull(compareBy<RiskyWindow> { it.millis }.thenByDescending { it.startHour }.thenBy { it.app })
}
