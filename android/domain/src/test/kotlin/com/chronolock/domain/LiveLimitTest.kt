package com.chronolock.domain

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LiveLimitTest {
    private val clock = ClockReading(1_800_000_000_000, 1000, 1, ZoneId.of("Asia/Seoul"))
    private fun state(target: String?, decision: RuleDecision = RuleDecision.Allow) = MonitorState(
        observedPackage = target, active = target?.let { ActiveSession(it, 30_000) }, decision = decision, clock = clock,
        daily = mapOf(DailyKey("a", clock.date) to DailyUsage(600_000, 2), DailyKey("b", clock.date) to DailyUsage(300_000, 1)))

    @Test fun observedApp_showsWhatIsLeftOfEachLimit_andItsGroups() {
        val group = AppGroup(1, "SNS", mapOf("a" to "A", "b" to "B"), dailyLimitSeconds = 1200, maxOpenCount = 5)
        val rules = withGroups(mapOf("a" to AppRule("a", "A", dailyLimitSeconds = 3600, maxOpenCount = 3, sessionLimitSeconds = 60)), listOf(group))
        val live = state("a").liveLimit(rules)!!
        assertEquals(3_000_000L, live.dailyLeftMillis)
        assertEquals(30_000L, live.sessionLeftMillis)
        assertEquals(1, live.opensLeft)
        assertEquals(listOf(GroupLeft("SNS", 300_000, 2)), live.groups)
        assertEquals(30_000L, live.nearestMillis)
    }

    @Test fun nothingShows_withoutALimitingRule_whilePaused_orOnceBlocked() {
        assertNull(state("a").liveLimit(emptyMap()))
        assertNull(state("a").liveLimit(mapOf("a" to AppRule("a", "A", schedules = listOf()) .copy(dailyLimitSeconds = 60, pausedUntilEpochMillis = clock.epochMillis + 1))))
        assertNull(state("a", RuleDecision(DecisionType.BLOCK)).liveLimit(mapOf("a" to AppRule("a", "A", dailyLimitSeconds = 60))))
        assertNull(state(null).liveLimit(mapOf("a" to AppRule("a", "A", dailyLimitSeconds = 60))))
    }
}
