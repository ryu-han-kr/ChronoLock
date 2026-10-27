package com.chronolock.data

import com.chronolock.domain.ClockReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClockAnchorsTest {
    private val zone = java.time.ZoneId.of("Asia/Seoul")
    private fun rule(focus: Long? = null, paused: Long? = null) =
        RuleEntity("a", "A", 60, null, null, focusUntilEpochMillis = focus, pausedUntilEpochMillis = paused)

    @Test fun focus_isShownUntilTheBootClockEndsIt() {
        // Focus ends at wall 10_000 and boot 2_000; the wall clock then jumped to 50_000 with 500 ms of focus left.
        val anchors = listOf(ClockAnchorEntity(ClockAnchors.FOCUS, 2_000, 3))
        val shown = ClockAnchors.shown(listOf(rule(focus = 10_000)), anchors, ClockReading(50_000, 1_500, 3, zone))
        assertEquals(50_500L, shown.single().focusUntilEpochMillis)
        // Another boot, or the boot clock already past the end: shown as stored.
        val stored = listOf(rule(focus = 10_000))
        assertSame(stored.single(), ClockAnchors.shown(stored, anchors, ClockReading(50_000, 100, 4, zone)).single())
        assertSame(stored.single(), ClockAnchors.shown(stored, anchors, ClockReading(50_000, 2_000, 3, zone)).single())
    }

    @Test fun pause_isShownEndingAtTheEarlierClock() {
        val anchors = listOf(ClockAnchorEntity(ClockAnchors.pause("a"), 2_000, 3))
        // The wall clock moved back: the boot clock ends the pause first.
        assertEquals(1_500L, ClockAnchors.shown(listOf(rule(paused = 10_000)), anchors, ClockReading(1_000, 1_500, 3, zone)).single().pausedUntilEpochMillis)
        // The boot clock already reached the end.
        assertNull(ClockAnchors.shown(listOf(rule(paused = 10_000)), anchors, ClockReading(1_000, 2_000, 3, zone)).single().pausedUntilEpochMillis)
        // The wall clock moved forward: the stored end comes first and stays.
        assertEquals(10_000L, ClockAnchors.shown(listOf(rule(paused = 10_000)), anchors, ClockReading(9_000, 1_000, 3, zone)).single().pausedUntilEpochMillis)
    }
}
