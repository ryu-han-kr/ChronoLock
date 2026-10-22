package com.chronolock.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotTest {
    private val hour = 3_600_000L

    @Test fun noRecords_sleeps() = assertEquals(Mood.SLEEPY, moodFor(0, hasRecords = false))
    @Test fun underAnHour_isRelaxed() = assertEquals(Mood.RELAXED, moodFor(hour - 1, hasRecords = true))
    @Test fun aroundTwoHours_isHappy() = assertEquals(Mood.HAPPY, moodFor(hour * 2, hasRecords = true))
    @Test fun fourHours_isSurprised() = assertEquals(Mood.SURPRISED, moodFor(hour * 4, hasRecords = true))
    @Test fun fiveHoursOrMore_isDizzy() = assertEquals(Mood.DIZZY, moodFor(hour * 5, hasRecords = true))
    @Test fun everyMood_hasAMessage() = Mood.values().forEach { assert(it.message.isNotBlank()) }
    @Test fun lines_rotateByDay_andStayWithinTheMood() {
        val day = java.time.LocalDate.of(2026, 10, 22)
        val lines = MascotLines.lines.getValue(Mood.HAPPY)
        assertEquals(lines[day.toEpochDay().mod(lines.size)], Mood.HAPPY.line(day))
        assert(Mood.HAPPY.line(day.plusDays(1)) in lines)
    }
    @Test fun manyShortVisits_suggestFewerOpens() =
        assertEquals(com.chronolock.domain.RuleRecipe.LESS_CHECKING, recommendRecipe(usageMillis = 60 * 60_000L, opens = 60, recordedDays = 7))
    @Test fun longSessions_suggestShortBreaks() =
        assertEquals(com.chronolock.domain.RuleRecipe.SHORT_BREAK, recommendRecipe(usageMillis = 3 * 60 * 60_000L, opens = 6, recordedDays = 1))
}
