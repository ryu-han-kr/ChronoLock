package com.chronolock.domain

/** Who the app is set up for at first run; each picks the goals that usually matter in that situation. */
enum class Situation(val emoji: String, val label: String, val goals: Set<OnboardingGoal>) {
    STUDY("📚", "공부·시험 준비", setOf(OnboardingGoal.SOCIAL, OnboardingGoal.VIDEO, OnboardingGoal.GAMES)),
    WORK("💼", "업무 집중", setOf(OnboardingGoal.SOCIAL, OnboardingGoal.SHOPPING)),
    BEDTIME("🌙", "잠들기 전 휴대폰", setOf(OnboardingGoal.SLEEP, OnboardingGoal.VIDEO)),
}

/**
 * What a shared link carries into a new install through the Play install referrer: the friend's goals ([fromFriend]) or
 * the situation a store listing was made for. Nothing personal travels; the referrer is fixed text in the link.
 */
data class Invite(val goals: Set<OnboardingGoal>, val situation: Situation?, val fromFriend: Boolean) {
    companion object {
        private const val SOURCE_INVITE = "invite"

        /** The Play link a person shares: the app's page plus their goals, URL-encoded as Play expects. */
        fun link(goals: Set<OnboardingGoal>): String {
            val referrer = "utm_source=$SOURCE_INVITE&utm_medium=app" +
                if (goals.isEmpty()) "" else "&utm_content=" + goals.sortedBy { it.ordinal }.joinToString(".") { it.name }
            return "https://play.google.com/store/apps/details?id=com.chronolock.app&referrer=" +
                java.net.URLEncoder.encode(referrer, "UTF-8")
        }

        /** Reads a referrer like `utm_source=invite&utm_content=SOCIAL.VIDEO` or `utm_campaign=study`; null when it holds neither. */
        fun parse(referrer: String?): Invite? {
            if (referrer.isNullOrBlank()) return null
            val fields = referrer.split('&').mapNotNull { part ->
                val at = part.indexOf('=')
                if (at <= 0) null else runCatching { part.substring(0, at) to java.net.URLDecoder.decode(part.substring(at + 1), "UTF-8") }.getOrNull()
            }.toMap()
            val goals = fields["utm_content"].orEmpty().split('.')
                .mapNotNull { name -> OnboardingGoal.entries.firstOrNull { it.name == name } }.toSet()
            val situation = Situation.entries.firstOrNull { it.name.equals(fields["utm_campaign"], ignoreCase = true) }
            if (goals.isEmpty() && situation == null) return null
            return Invite(goals.ifEmpty { situation!!.goals }, situation, fromFriend = fields["utm_source"] == SOURCE_INVITE && goals.isNotEmpty())
        }
    }
}
