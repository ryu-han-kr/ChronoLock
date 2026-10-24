package com.chronolock.domain

/**
 * Apps that share one daily allowance: time and opens of every member count against the same limits, and the
 * group's [schedules] block every member at once. [members] maps package names to display names.
 * An app belongs to at most one group ([takenBy]); groups saved before that rule may still overlap, and then the
 * limits of each apply.
 */
data class AppGroup(
    val id: Long,
    val name: String,
    val members: Map<String, String>,
    val dailyLimitSeconds: Long? = null,
    val maxOpenCount: Int? = null,
    val enabled: Boolean = true,
    val schedules: List<Schedule> = emptyList(),
) {
    init {
        require(name.isNotBlank() && members.size in 2..MAX_MEMBERS)
        require(dailyLimitSeconds == null || dailyLimitSeconds in 1..86400)
        require(maxOpenCount == null || maxOpenCount in 1..10000)
        require(schedules.size <= AppRule.MAX_SCHEDULES)
        require(dailyLimitSeconds != null || maxOpenCount != null || schedules.isNotEmpty())
    }

    companion object { const val MAX_MEMBERS = 200 }
}

/** Each app already in another group than [exceptId], mapped to that group's name. */
fun List<AppGroup>.takenBy(exceptId: Long): Map<String, String> =
    filter { it.id != exceptId }.flatMap { group -> group.members.keys.map { it to group.name } }.toMap()

/** Editor input for a group; [daily] is seconds as text and blank limits mean unrestricted. */
data class GroupDraft(val name: String = "", val members: Map<String, String> = emptyMap(), val daily: String = "3600", val opens: String = "",
    val enabled: Boolean = true, val scheduleEnabled: Boolean = false, val schedules: List<ScheduleDraft> = listOf(ScheduleDraft())) {
    fun build(id: Long): AppGroup {
        fun limit(value: String, maximum: Long, label: String): Long? {
            if (value.isBlank()) return null
            val parsed = value.trim().toLongOrNull()
            require(parsed != null && parsed in 1..maximum) { "$label: 1~${maximum} 범위의 정수를 입력해 주세요." }
            return parsed
        }
        require(name.isNotBlank()) { "그룹 이름을 입력해 주세요." }
        require(members.size >= 2) { "묶을 앱을 두 개 이상 선택해 주세요." }
        require(members.size <= AppGroup.MAX_MEMBERS) { "한 그룹에는 앱을 최대 200개까지 넣을 수 있습니다." }
        val dailySeconds = limit(daily, 86400, "하루 시간(초)")
        val openCount = limit(opens, 10000, "실행 횟수")?.toInt()
        val windows = if (scheduleEnabled) {
            require(schedules.isNotEmpty()) { "예약 구간을 하나 이상 추가해 주세요." }
            require(schedules.size <= AppRule.MAX_SCHEDULES) { "예약 구간은 최대 5개까지 설정할 수 있습니다." }
            schedules.map { it.build() }.distinct()
        } else emptyList()
        require(dailySeconds != null || openCount != null || windows.isNotEmpty()) { "함께 쓸 하루 시간, 실행 횟수나 차단 시간대를 설정해 주세요." }
        return AppGroup(id, name.trim(), members, dailySeconds, openCount, enabled, windows)
    }

    companion object {
        fun from(group: AppGroup) = GroupDraft(group.name, group.members, group.dailyLimitSeconds?.toString().orEmpty(),
            group.maxOpenCount?.toString().orEmpty(), group.enabled, group.schedules.isNotEmpty(),
            group.schedules.map(ScheduleDraft::from).ifEmpty { listOf(ScheduleDraft()) })
    }
}

/** Today's combined usage of a group's members. */
fun MonitorState.groupUsage(group: AppGroup): DailyUsage {
    val date = clock?.date ?: return DailyUsage()
    return group.members.keys.map { daily[DailyKey(it, date)] ?: DailyUsage() }
        .fold(DailyUsage()) { sum, day -> DailyUsage(sum.usageMillis + day.usageMillis, sum.opens + day.opens, sum.blockedAttempts + day.blockedAttempts) }
}

/**
 * The rules the monitor evaluates: each app's own rule, plus every member of an enabled group carrying all its groups.
 * A member without an enabled rule of its own gets an empty one so the group still tracks and limits it.
 */
fun withGroups(rules: Map<String, AppRule>, groups: List<AppGroup>): Map<String, AppRule> {
    val result = rules.toMutableMap()
    groups.filter { it.enabled }.forEach { group ->
        group.members.forEach { (app, name) ->
            val current = result[app]?.takeIf { it.groups.isNotEmpty() } ?: rules[app]?.takeIf { it.enabled } ?: AppRule(app, name)
            result[app] = current.copy(groups = current.groups + group)
        }
    }
    return result
}
