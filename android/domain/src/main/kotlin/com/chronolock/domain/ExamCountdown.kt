package com.chronolock.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** An exam the person is studying for: its [date] and an optional [name] such as "수능". */
data class Exam(val date: LocalDate, val name: String = "") {
    /** Days until the exam: 0 on the day, negative once it is over. */
    fun daysLeft(today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** Shown until the day itself; afterwards the countdown goes away on its own. */
    fun showing(today: LocalDate): Boolean = daysLeft(today) >= 0

    /** In the last [CLOSE_DAYS] days, Today suggests tightening limits; it never changes them by itself. */
    fun close(today: LocalDate): Boolean = daysLeft(today) in 0..CLOSE_DAYS

    /** "D-21", or "D-DAY" on the day. */
    fun label(today: LocalDate): String = daysLeft(today).let { if (it == 0L) "D-DAY" else "D-$it" }

    companion object {
        const val CLOSE_DAYS = 14L
        const val MAX_NAME = 20

        /** Stored as `2026-11-19|수능`; null for anything unreadable. */
        fun decode(text: String?): Exam? {
            val parts = text?.split('|', limit = 2) ?: return null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            return Exam(date, parts.getOrNull(1).orEmpty().take(MAX_NAME))
        }

        fun encode(exam: Exam): String = "${exam.date}|${exam.name.replace("|", "").take(MAX_NAME)}"
    }
}
