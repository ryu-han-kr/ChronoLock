package com.chronolock.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamCountdownTest {
    private val exam = Exam(LocalDate.of(2026, 11, 19), "수능")

    @Test fun countsDown_toTheDay_thenGoesAway() {
        assertEquals("D-20", exam.label(LocalDate.of(2026, 10, 30)))
        assertEquals("D-DAY", exam.label(LocalDate.of(2026, 11, 19)))
        assertTrue(exam.showing(LocalDate.of(2026, 11, 19)))
        assertFalse(exam.showing(LocalDate.of(2026, 11, 20)))
    }

    @Test fun lastTwoWeeks_suggestTightening() {
        assertFalse(exam.close(LocalDate.of(2026, 11, 4)))
        assertTrue(exam.close(LocalDate.of(2026, 11, 5)))
        assertTrue(exam.close(LocalDate.of(2026, 11, 19)))
        assertFalse(exam.close(LocalDate.of(2026, 11, 20)))
    }

    @Test fun storage_roundTrips_andRejectsJunk() {
        assertEquals(exam, Exam.decode(Exam.encode(exam)))
        assertEquals(Exam(exam.date, "AB"), Exam.decode(Exam.encode(Exam(exam.date, "A|B"))))
        assertEquals(Exam(exam.date), Exam.decode("2026-11-19"))
        assertNull(Exam.decode("nope|x"))
        assertNull(Exam.decode(null))
    }
}
