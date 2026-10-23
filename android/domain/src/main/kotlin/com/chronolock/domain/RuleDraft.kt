package com.chronolock.domain

import java.time.DayOfWeek
import java.time.LocalTime

/** One schedule window as typed in the editor; [daysMask] has bit 0 for Monday through bit 6 for Sunday. */
data class ScheduleDraft(val start: String = "23:00", val end: String = "07:00", val daysMask: Int = 0) {
    fun build(): Schedule {
        fun time(value: String): LocalTime {
            val text = value.trim()
            require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(text)) { "예약 시각은 23:00처럼 00:00~23:59로 입력해 주세요." }
            return LocalTime.parse(text)
        }
        val from = time(start)
        val until = time(end)
        require(from != until) { "예약 시작과 종료 시각을 다르게 설정해 주세요." }
        require(daysMask in 1..127) { "예약할 요일을 하나 이상 선택해 주세요." }
        return Schedule(from, until, DayOfWeek.values().filter { daysMask and (1 shl (it.value - 1)) != 0 }.toSet())
    }

    companion object {
        const val EVERY_DAY = 127
        const val WEEKDAYS = 31
        const val WEEKEND = 96
        fun from(schedule: Schedule) = ScheduleDraft(schedule.start.toString(), schedule.end.toString(),
            schedule.days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) })
    }
}

/** Editor input validated before a rule can be saved. Empty limits mean unrestricted. */
data class RuleDraft(
    val daily: String = "3600",
    val opens: String = "",
    val cooldown: String = "",
    val session: String = "",
    val scheduleEnabled: Boolean = false,
    val schedules: List<ScheduleDraft> = listOf(ScheduleDraft()),
    val enabled: Boolean = true,
    /** 0 means off. */
    val pauseSeconds: Int = 0,
    val checkInMinutes: Int = 0,
) {
    fun build(packageName: String, appName: String): AppRule {
        fun limit(value: String, maximum: Long, label: String): Long? {
            if (value.isBlank()) return null
            val parsed = value.trim().toLongOrNull()
            require(parsed != null && parsed in 1..maximum) { "$label: 1~${maximum} 범위의 정수를 입력해 주세요." }
            return parsed
        }
        val dailySeconds = limit(daily, 86400, "하루 시간(초)")
        val openCount = limit(opens, 10000, "실행 횟수")?.toInt()
        val cooldownSeconds = limit(cooldown, 86400, "재실행 대기(초)")
        val sessionSeconds = limit(session, 86400, "1회 사용시간(초)")
        val built = if (scheduleEnabled) {
            require(schedules.isNotEmpty()) { "예약 구간을 하나 이상 추가해 주세요." }
            require(schedules.size <= AppRule.MAX_SCHEDULES) { "예약 구간은 최대 5개까지 설정할 수 있습니다." }
            schedules.map { it.build() }.distinct()
        } else emptyList()
        require(dailySeconds != null || openCount != null || cooldownSeconds != null || sessionSeconds != null || built.isNotEmpty() ||
            pauseSeconds > 0 || checkInMinutes > 0) {
            "한 가지 이상의 제한을 설정해 주세요."
        }
        return AppRule(packageName, appName, dailySeconds, openCount, cooldownSeconds, sessionSeconds, built, enabled = enabled,
            pauseSeconds = pauseSeconds.takeIf { it > 0 }, checkInMinutes = checkInMinutes.takeIf { it > 0 })
    }

    companion object {
        fun from(rule: AppRule) = RuleDraft(
            rule.dailyLimitSeconds?.toString().orEmpty(), rule.maxOpenCount?.toString().orEmpty(),
            rule.cooldownSeconds?.toString().orEmpty(), rule.sessionLimitSeconds?.toString().orEmpty(),
            rule.schedules.isNotEmpty(), rule.schedules.map(ScheduleDraft::from).ifEmpty { listOf(ScheduleDraft()) }, rule.enabled,
            rule.pauseSeconds ?: 0, rule.checkInMinutes ?: 0,
        )
    }
}

enum class RuleRecipe(val title: String, val description: String, val draft: RuleDraft) {
    SHORT_BREAK("짧게 보고 쉬기", "한 번에 5분 · 다시 열기 전 10분 대기", RuleDraft(daily = "", session = "300", cooldown = "600")),
    LESS_CHECKING("확인 횟수 줄이기", "하루 5회 · 한 번에 3분", RuleDraft(daily = "", opens = "5", session = "180")),
    SLEEP("취침 시간 지키기", "매일 23:00~다음 날 07:00 차단",
        RuleDraft(daily = "", scheduleEnabled = true, schedules = listOf(ScheduleDraft(daysMask = ScheduleDraft.EVERY_DAY)))),
}

/** What the person wants to cut down on, asked at first run; each suggests a recipe and, for websites, a site preset. */
enum class OnboardingGoal(val emoji: String, val label: String, val recipe: RuleRecipe?, val preset: SitePreset?) {
    SOCIAL("💬", "SNS", RuleRecipe.LESS_CHECKING, SitePreset.SOCIAL),
    VIDEO("📺", "영상", RuleRecipe.SHORT_BREAK, SitePreset.VIDEO),
    GAMES("🎮", "게임", null, null),
    SHOPPING("🛒", "쇼핑·커뮤니티", RuleRecipe.LESS_CHECKING, SitePreset.SHOPPING),
    SLEEP("🌙", "수면", RuleRecipe.SLEEP, null),
}
