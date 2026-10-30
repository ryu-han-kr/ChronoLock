package com.chronolock.domain

/**
 * Limits over the whole phone that the person sets for themselves, on top of per-app rules: [windows] when only the
 * apps they keep outside the policy may be used, and a [dailyBudgetSeconds] for all the other apps together. [apps] are
 * the apps it covers: the launchable ones, minus the safety apps, ChronoLock itself and the ones the person always allows.
 */
data class PhonePolicy(
    val windows: List<Schedule> = emptyList(),
    val dailyBudgetSeconds: Long? = null,
    val apps: Set<String> = emptySet(),
) {
    val active: Boolean get() = windows.isNotEmpty() || dailyBudgetSeconds != null

    fun covers(packageName: String?): Boolean = active && packageName != null && packageName in apps

    /** Today's time in the covered apps together. */
    fun usedToday(daily: Map<DailyKey, DailyUsage>, now: ClockReading): Long =
        daily.entries.filter { it.key.date == now.date && it.key.packageName in apps }.sumOf { it.value.usageMillis }

    /** Why [target] may not be used now under this policy; empty when it may. */
    fun reasons(target: String, now: ClockReading, daily: Map<DailyKey, DailyUsage>): List<Reason> {
        if (!covers(target)) return emptyList()
        return buildList {
            if (windows.any { it.contains(now.epochMillis, now.zone) }) add(Reason.ALLOWED_ONLY)
            if (dailyBudgetSeconds?.let { usedToday(daily, now) >= it * 1000 } == true) add(Reason.PHONE_BUDGET)
        }
    }

    /** Whether [next] lets more through than this one: a window or budget dropped or eased, or fewer apps covered. */
    fun loosenedBy(next: PhonePolicy): Boolean {
        val windowsDropped = windows.any { it !in next.windows }
        val budgetEased = dailyBudgetSeconds != null && (next.dailyBudgetSeconds == null || next.dailyBudgetSeconds > dailyBudgetSeconds)
        return windowsDropped || budgetEased
    }

    companion object {
        val NONE = PhonePolicy()
        /** A phone-wide budget from 30 minutes to 16 hours. */
        val BUDGET_SECONDS = 30 * 60L..16 * 3600L
    }
}
