package com.chronolock.domain

/**
 * Finishing a focus of at least [MIN_FOCUS_MILLIS] without stopping it banks [REWARD_MINUTES], up to [MAX_MINUTES].
 * Banked minutes are spent as a short pause of one app, so they never raise a daily limit.
 */
object TimeBank {
    const val REWARD_MINUTES = 10
    const val MAX_MINUTES = 30
    const val MIN_FOCUS_MILLIS = 25 * 60_000L

    /**
     * Minutes earned by the focus that ends at [until] and began at [startedAt]: none while it runs, when it was
     * already counted ([credited] is the end last counted), or when it was shorter than [MIN_FOCUS_MILLIS].
     */
    fun earned(until: Long?, startedAt: Long?, credited: Long?, nowEpochMillis: Long): Int = when {
        until == null || startedAt == null || nowEpochMillis < until || until == credited -> 0
        until - startedAt < MIN_FOCUS_MILLIS -> 0
        else -> REWARD_MINUTES
    }

    fun add(bank: Int, minutes: Int): Int = (bank + minutes).coerceIn(0, MAX_MINUTES)
}
