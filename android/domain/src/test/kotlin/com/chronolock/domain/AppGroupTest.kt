package com.chronolock.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AppGroupTest {
    private val monitor = UsageMonitor()
    private val zone = ZoneId.of("Asia/Seoul")
    private val origin = Instant.parse("2026-10-08T09:00:00Z").toEpochMilli()
    private fun clock(ms: Long) = ClockReading(origin + ms, ms, 1, zone)
    private val group = AppGroup(1, "SNS", mapOf("a" to "A", "b" to "B"), dailyLimitSeconds = 60, maxOpenCount = 3)

    @Test fun sharedTime_blocksEveryMemberOnceTheSumIsUsed() {
        val rules = withGroups(emptyMap(), listOf(group))
        var state = monitor.foreground(MonitorState(), "a", clock(0), rules).state
        state = monitor.tick(state, clock(40000), rules).state
        state = monitor.foreground(state, "b", clock(40000), rules).state
        state = monitor.tick(state, clock(59999), rules).state
        assertFalse(state.decision.denied)
        state = monitor.tick(state, clock(60000), rules).state
        assertEquals(listOf(Reason.GROUP_USAGE), state.decision.reasons)
        state = monitor.foreground(state, "a", clock(61000), rules).state
        assertTrue(state.decision.denied)
        assertEquals(60000L, state.groupUsage(group).usageMillis)
    }

    @Test fun sharedOpens_countAcrossMembers() {
        val rules = withGroups(emptyMap(), listOf(group))
        var state = MonitorState()
        listOf("a", "b", "a").forEachIndexed { index, app ->
            state = monitor.foreground(state, app, clock(index * 2000L), rules).state
            state = monitor.foreground(state, null, clock(index * 2000L + 1000), rules).state
        }
        state = monitor.foreground(state, "b", clock(10000), rules).state
        assertEquals(listOf(Reason.GROUP_OPENS), state.decision.reasons)
        assertEquals(3, state.groupUsage(group).opens)
    }

    @Test fun ownRulesStayInForceAndDisabledGroupsAreIgnored() {
        val own = AppRule("a", "A", maxOpenCount = 1)
        val combined = withGroups(mapOf("a" to own, "c" to AppRule("c", "C")), listOf(group, group.copy(id = 2, members = mapOf("c" to "C", "d" to "D"), enabled = false)))
        assertEquals(1, combined.getValue("a").maxOpenCount)
        assertEquals(listOf(group), combined.getValue("a").groups)
        assertEquals(AppRule("b", "B", groups = listOf(group)), combined.getValue("b"))
        assertTrue(combined.getValue("c").groups.isEmpty())
        assertFalse("d" in combined)
        // A switched-off own rule drops its own limits but not the group's.
        val off = withGroups(mapOf("a" to own.copy(enabled = false)), listOf(group)).getValue("a")
        assertEquals(AppRule("a", "A", groups = listOf(group)), off)
    }

    @Test fun appInTwoGroups_isBlockedWhenEitherIsUsedUp() {
        val video = AppGroup(2, "Video", mapOf("a" to "A", "c" to "C"), maxOpenCount = 1)
        val rules = withGroups(emptyMap(), listOf(group, video))
        assertEquals(listOf(group, video), rules.getValue("a").groups)
        // "c" uses up the video group's single open; "a" shares it, so "a" is blocked although the SNS group has room.
        var state = monitor.foreground(MonitorState(), "c", clock(0), rules).state
        state = monitor.foreground(state, null, clock(1000), rules).state
        state = monitor.foreground(state, "a", clock(2000), rules).state
        assertEquals(listOf(Reason.GROUP_OPENS), state.decision.reasons)
    }

    @Test fun pausedMember_isNotLimitedByTheGroup() {
        val paused = AppRule("a", "A", maxOpenCount = 9, pausedUntilEpochMillis = origin + 600_000)
        val rules = withGroups(mapOf("a" to paused), listOf(group.copy(dailyLimitSeconds = 1)))
        var state = monitor.foreground(MonitorState(), "a", clock(0), rules).state
        state = monitor.tick(state, clock(5000), rules).state
        assertFalse(state.decision.denied)
    }

    @Test fun draft_validatesNameMembersAndLimits() {
        val members = mapOf("a" to "A", "b" to "B")
        assertEquals(AppGroup(0, "SNS", members, 3600), GroupDraft(" SNS ", members).build(0))
        assertEquals("그룹 이름을 입력해 주세요.", runCatching { GroupDraft("", members).build(0) }.exceptionOrNull()?.message)
        assertEquals("묶을 앱을 두 개 이상 선택해 주세요.", runCatching { GroupDraft("SNS", mapOf("a" to "A")).build(0) }.exceptionOrNull()?.message)
        assertEquals("함께 쓸 하루 시간, 실행 횟수나 차단 시간대를 설정해 주세요.", runCatching { GroupDraft("SNS", members, daily = "").build(0) }.exceptionOrNull()?.message)
        val saved = AppGroup(4, "SNS", members, null, 7, enabled = false)
        assertEquals(saved, GroupDraft.from(saved).build(4))
        // Block hours alone are enough of a limit, and survive a round trip through the editor.
        val night = GroupDraft("SNS", members, daily = "", scheduleEnabled = true, schedules = listOf(ScheduleDraft("23:00", "07:00", ScheduleDraft.EVERY_DAY))).build(5)
        assertEquals(1, night.schedules.size)
        assertEquals(night, GroupDraft.from(night).build(5))
    }

    @Test fun groupSchedule_blocksEveryMemberAndRemovingItLoosens() {
        val always = Schedule(java.time.LocalTime.MIDNIGHT, java.time.LocalTime.of(23, 59), java.time.DayOfWeek.values().toSet())
        val night = AppGroup(9, "Night", mapOf("a" to "A", "b" to "B"), schedules = listOf(always))
        val rule = withGroups(emptyMap(), listOf(night)).getValue("b")
        val decision = RuleEngine().evaluate(RuleContext(java.time.Instant.parse("2026-10-24T03:00:00Z").toEpochMilli(), java.time.ZoneOffset.UTC), rule)
        assertEquals(listOf(Reason.SCHEDULE), decision.reasons)
        assertTrue(Strictness.loosens(night, night.copy(schedules = emptyList(), dailyLimitSeconds = 60)))
        assertFalse(Strictness.loosens(night.copy(schedules = emptyList(), dailyLimitSeconds = 60), night.copy(dailyLimitSeconds = 60)))
    }

    @Test fun takenBy_namesTheOtherGroupOfEachApp() {
        val sns = AppGroup(1, "SNS", mapOf("a" to "A", "b" to "B"), 60)
        val video = AppGroup(2, "Video", mapOf("c" to "C", "d" to "D"), 60)
        assertEquals(mapOf("c" to "Video", "d" to "Video"), listOf(sns, video).takenBy(1))
        assertEquals(mapOf("a" to "SNS", "b" to "SNS", "c" to "Video", "d" to "Video"), listOf(sns, video).takenBy(0))
    }

    @Test fun strictness_holdsOnlyRelaxingGroupChanges() {
        assertFalse(Strictness.loosens(null as AppGroup?, group))
        assertFalse(Strictness.loosens(group, group.copy(dailyLimitSeconds = 30, members = group.members + ("c" to "C"), name = "새 이름")))
        assertTrue(Strictness.loosens(group, null))
        assertTrue(Strictness.loosens(group, group.copy(enabled = false)))
        assertTrue(Strictness.loosens(group, group.copy(dailyLimitSeconds = 120)))
        assertTrue(Strictness.loosens(group, group.copy(maxOpenCount = null, dailyLimitSeconds = 60)))
        assertTrue(Strictness.loosens(group, group.copy(members = mapOf("a" to "A", "c" to "C"))))
    }

    @Test fun dayResults_countAGroupsSharedLimit() {
        val day = java.time.LocalDate.of(2026, 10, 20)
        val sns = AppGroup(3, "SNS", mapOf("a" to "A", "b" to "B"), dailyLimitSeconds = 60)
        val rules = withGroups(emptyMap(), listOf(sns)).values.toList()
        val over = mapOf(DailyKey("a", day) to DailyUsage(40_000, 1, 0), DailyKey("b", day) to DailyUsage(30_000, 1, 0))
        assertEquals(DayResult.REACHED, dayResults(over, rules, day, 1).single().second)
        val under = mapOf(DailyKey("a", day) to DailyUsage(20_000, 1, 0))
        assertEquals(DayResult.KEPT, dayResults(under, rules, day, 1).single().second)
    }
}
