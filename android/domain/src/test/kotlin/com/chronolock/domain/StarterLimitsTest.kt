package com.chronolock.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StarterLimitsTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val minute = 60_000L
    private fun week(app: String, perDay: Long, days: Int = 7) = (1..days).associate { DailyKey(app, today.minusDays(it.toLong())) to perDay * minute }

    @Test fun topThreeUnlimitedApps_getSeventyPercent() {
        val usage = week("video", 130) + week("chat", 60) + week("shop", 40) + week("game", 30) + week("limited", 300)
        val limits = starterLimits(usage, setOf("video", "chat", "shop", "game"), today)
        assertEquals(listOf("video", "chat", "shop"), limits.map { it.app })
        // 130 min → 91 → 90 min; 60 → 42 → 40; 40 → 28 → 25.
        assertEquals(listOf(90L, 40L, 25L), limits.map { it.limitSeconds / 60 })
    }

    @Test fun lightUse_aShortRecord_orTodayAlone_suggestNothing() {
        assertEquals(emptyList<StarterLimit>(), starterLimits(week("video", 120, days = 2), setOf("video"), today))
        assertEquals(emptyList<StarterLimit>(), starterLimits(week("video", 10), setOf("video"), today))
        val todayOnly = mapOf(DailyKey("video", today) to 500 * minute)
        assertEquals(emptyList<StarterLimit>(), starterLimits(todayOnly, setOf("video"), today))
    }

    @Test fun limits_neverGoUnderFifteenMinutes() {
        assertEquals(20L, starterLimits(week("video", 29), setOf("video"), today).single().limitSeconds / 60)
        assertEquals(15L, starterLimits(week("video", 20), setOf("video"), today).single().limitSeconds / 60)
    }
}
