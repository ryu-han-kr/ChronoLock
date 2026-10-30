package com.chronolock.domain

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Notifications from limited apps held back and shown together at [HOURS]. The system snoozes the original
 * notification until then, so nothing is read, stored or lost.
 */
object NotificationBatch {
    val HOURS = listOf(12, 18, 21)

    /** The next delivery strictly after [now]. */
    fun nextDelivery(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        return HOURS.map { today.atTime(LocalTime.of(it, 0)) }.firstOrNull { it.isAfter(now) }
            ?: today.plusDays(1).atTime(LocalTime.of(HOURS.first(), 0))
    }
}
