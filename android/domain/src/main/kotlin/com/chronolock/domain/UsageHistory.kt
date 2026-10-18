package com.chronolock.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryEventType { RESUME, PAUSE, SCREEN_OFF, SHUTDOWN, STARTUP }
data class HistoryEvent(val at: Long, val type: HistoryEventType, val app: String = "", val activity: String = "")
data class HistoryUsage(val millis: Long = 0, val opens: Int = 0)

/** Foreground visits, independent of blocking rules. Same-app activity changes within 1s are one visit. */
fun aggregateHistory(events: List<HistoryEvent>, from: Long, until: Long, zone: ZoneId): Map<DailyKey, HistoryUsage> {
    require(until >= from)
    val result = mutableMapOf<DailyKey, HistoryUsage>()
    var active: HistoryEvent? = null
    var previousApp: String? = null
    var pausedAt = Long.MIN_VALUE
    fun date(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    fun close(at: Long) {
        active?.let { current ->
            var cursor = maxOf(current.at, from)
            val end = minOf(at, until)
            while (cursor < end) {
                val day = date(cursor)
                val boundary = minOf(end, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val key = DailyKey(current.app, day)
                val old = result[key] ?: HistoryUsage()
                result[key] = old.copy(millis = old.millis + boundary - cursor)
                cursor = boundary
            }
        }
        active = null
    }
    events.sortedBy { it.at }.forEach { event ->
        if (event.at >= until) return@forEach
        when (event.type) {
            HistoryEventType.RESUME -> {
                if (event.app.isBlank()) return@forEach
                val sameVisit = active?.app == event.app ||
                    (previousApp == event.app && pausedAt != Long.MIN_VALUE && event.at - pausedAt in 0..1000)
                close(event.at)
                if (!sameVisit && event.at >= from) {
                    val key = DailyKey(event.app, date(event.at))
                    val old = result[key] ?: HistoryUsage()
                    result[key] = old.copy(opens = old.opens + 1)
                }
                active = event
                previousApp = event.app
                pausedAt = Long.MIN_VALUE
            }
            HistoryEventType.PAUSE -> if (active?.app == event.app &&
                (event.activity.isBlank() || active?.activity == event.activity)) {
                close(event.at)
                pausedAt = event.at
            }
            HistoryEventType.SCREEN_OFF, HistoryEventType.SHUTDOWN -> {
                close(event.at)
                previousApp = null
                pausedAt = Long.MIN_VALUE
            }
            HistoryEventType.STARTUP -> {
                // A missing shutdown event gives no reliable end time. Never count powered-off time.
                active = null
                previousApp = null
                pausedAt = Long.MIN_VALUE
            }
        }
    }
    close(until)
    return result
}
