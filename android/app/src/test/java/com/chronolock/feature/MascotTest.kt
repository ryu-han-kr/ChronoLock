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
}
