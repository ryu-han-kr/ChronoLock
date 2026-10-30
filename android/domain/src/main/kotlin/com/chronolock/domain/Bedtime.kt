package com.chronolock.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Plugging the phone in at night offers a focus until morning: once a night, between [FROM] and [UNTIL] the next day,
 * and the focus then runs to [MORNING].
 */
object Bedtime {
    val FROM: LocalTime = LocalTime.of(21, 0)
    val UNTIL: LocalTime = LocalTime.of(3, 0)
    val MORNING: LocalTime = LocalTime.of(7, 0)

    /** The evening a moment belongs to (after midnight counts as the night before), or null outside the window. */
    fun night(now: LocalDateTime): LocalDate? = when {
        !now.toLocalTime().isBefore(FROM) -> now.toLocalDate()
        now.toLocalTime().isBefore(UNTIL) -> now.toLocalDate().minusDays(1)
        else -> null
    }

    /** True when charging at [now] should ask, given the night last asked about. */
    fun promptDue(now: LocalDateTime, lastNight: LocalDate?): Boolean = night(now)?.let { it != lastNight } ?: false

    /** The next [MORNING] after [now]. */
    fun morning(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(MORNING)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }
}
