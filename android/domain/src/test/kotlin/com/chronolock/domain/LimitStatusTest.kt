package com.chronolock.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class LimitStatusTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ClockReading(ZonedDateTime.of(2026, 10, 21, 20, 0, 0, 0, zone).toInstant().toEpochMilli(), 0, 1, zone)
    private val today = now.date
    private val video = AppRule("video", "동영상", dailyLimitSeconds = 3600)
    private val chat = AppRule("chat", "메신저", dailyLimitSeconds = 1800)
    private val rules = mapOf(video.packageName to video, chat.packageName to chat)

    private fun state(observed: String?, vararg used: Pair<String, Long>, decision: RuleDecision = RuleDecision.Allow) = MonitorState(
        observedPackage = observed, clock = now, decision = decision,
        daily = used.associate { (app, millis) -> DailyKey(app, today) to DailyUsage(millis) })

    @Test fun warnsOnceWhenFiveMinutesOrLessRemain() {
        val nearly = state("video", "video" to 3_300_000)
        assertEquals(DailyKey("video", today), nearly.limitWarning(rules, emptySet()))
        assertNull(nearly.limitWarning(rules, setOf(DailyKey("video", today))))
    }

    @Test fun noWarningWithPlentyLeftOrLimitAlreadyReached() {
        assertNull(state("video", "video" to 3_000_000).limitWarning(rules, emptySet()))
        assertNull(state("video", "video" to 3_600_000).limitWarning(rules, emptySet()))
        assertNull(state("video", "video" to 3_400_000, decision = RuleDecision(DecisionType.BLOCK)).limitWarning(rules, emptySet()))
    }

    @Test fun noWarningForBackgroundDisabledOrShortLimits() {
        assertNull(state(null, "video" to 3_300_000).limitWarning(rules, emptySet()))
        val paused = mapOf("video" to video.copy(enabled = false))
        assertNull(state("video", "video" to 3_300_000).limitWarning(paused, emptySet()))
        val short = mapOf("video" to video.copy(dailyLimitSeconds = 120))
        assertNull(state("video", "video" to 60_000).limitWarning(short, emptySet()))
    }

    @Test fun summaryAddsLimitedAppsAndPicksTightestEnabledLimit() {
        val summary = state(null, "video" to 600_000, "chat" to 1_500_000, "other" to 999_000).todaySummary(rules)
        assertEquals(2_100_000L, summary.usedMillis)
        assertEquals("메신저", summary.tightestApp)
        assertEquals(300_000L, summary.tightestRemainingMillis)
    }

    @Test fun summaryWithoutClockOrDailyLimitsIsEmpty() {
        assertEquals(TodaySummary(0, null, null), MonitorState().todaySummary(rules))
        val countOnly = mapOf("chat" to AppRule("chat", "메신저", maxOpenCount = 3))
        assertNull(state(null, "chat" to 1000).todaySummary(countOnly).tightestApp)
    }
}
