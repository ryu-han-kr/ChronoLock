package com.chronolock.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenReasonsTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test fun taps_roundTripThroughStorage() {
        var counts = OpenReasons.add(emptyMap(), today, OpenReason.HABIT)
        counts = OpenReasons.add(counts, today, OpenReason.HABIT)
        counts = OpenReasons.add(counts, today.minusDays(1), OpenReason.BORED)
        val text = OpenReasons.encode(counts)
        assertEquals("2026-10-29:BORED:1;2026-10-30:HABIT:2", text)
        assertEquals(counts, OpenReasons.decode(text))
    }

    @Test fun oldDaysFallAway_andBrokenEntriesAreSkipped() {
        val old = mapOf(today.minusDays(OpenReasons.KEEP_DAYS) to mapOf(OpenReason.BORED to 3))
        assertEquals(setOf(today), OpenReasons.add(old, today, OpenReason.NEEDED).keys)
        assertEquals(mapOf(today to mapOf(OpenReason.HABIT to 1)),
            OpenReasons.decode("2026-10-30:HABIT:1;bad;2026-13-01:HABIT:1;2026-10-30:NOPE:2;2026-10-29:BORED:0"))
    }

    @Test fun weekShares_coverTheLastSevenDays() {
        val counts = mapOf(
            today to mapOf(OpenReason.HABIT to 4, OpenReason.BORED to 1),
            today.minusDays(6) to mapOf(OpenReason.BORED to 2, OpenReason.NOTIFICATION to 3),
            today.minusDays(7) to mapOf(OpenReason.NEEDED to 50),
        )
        assertEquals(listOf(OpenReason.HABIT to 40, OpenReason.BORED to 30, OpenReason.NOTIFICATION to 30), OpenReasons.weekShares(counts, today))
        assertEquals(emptyList<Pair<OpenReason, Int>>(), OpenReasons.weekShares(emptyMap(), today))
    }
}
