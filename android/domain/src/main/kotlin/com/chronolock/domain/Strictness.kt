package com.chronolock.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Strict mode delays every change that would let an app be used sooner, so an impulse cannot undo a limit at once.
 * Tightening still applies immediately.
 */
object Strictness {
    /** Delay choices in minutes; 0 turns strict mode off. */
    val DELAYS = listOf(0, 10, 30, 60)

    /** Whether replacing [current] with [next] (null deletes the rule) relaxes any restriction at [nowEpochMillis]. */
    fun loosens(current: AppRule?, next: AppRule?, nowEpochMillis: Long): Boolean {
        if (current == null || !current.enabled) return false
        if (next == null || !next.enabled) return true
        fun raised(old: Long?, new: Long?) = old != null && (new == null || new > old)
        fun lowered(old: Long?, new: Long?) = old != null && (new == null || new < old)
        val oldPause = current.pausedUntilEpochMillis?.coerceAtLeast(nowEpochMillis) ?: nowEpochMillis
        return raised(current.dailyLimitSeconds, next.dailyLimitSeconds) ||
            raised(current.sessionLimitSeconds, next.sessionLimitSeconds) ||
            raised(current.maxOpenCount?.toLong(), next.maxOpenCount?.toLong()) ||
            lowered(current.cooldownSeconds, next.cooldownSeconds) ||
            !next.schedules.containsAll(current.schedules) ||
            (next.pausedUntilEpochMillis ?: 0) > oldPause
    }

    /** Whether lowering the delay from [current] to [next] minutes must itself wait. */
    fun loosensDelay(current: Int, next: Int) = next < current

    /** End of a pause that starts at [startEpochMillis]: [minutes] later, or the next midnight when [minutes] is null. */
    fun pauseEnd(startEpochMillis: Long, minutes: Int?, zone: ZoneId): Long {
        if (minutes != null) {
            require(minutes in 1..1440)
            return startEpochMillis + minutes * 60_000L
        }
        return Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/** Strict mode as stored: the delay in force, and a lower delay that takes over at [pendingAtEpochMillis]. */
data class StrictSetting(val delayMinutes: Int = 0, val pendingMinutes: Int? = null, val pendingAtEpochMillis: Long? = null) {
    fun delayAt(epochMillis: Long): Int =
        if (pendingMinutes != null && pendingAtEpochMillis != null && epochMillis >= pendingAtEpochMillis) pendingMinutes else delayMinutes
    fun delayMillisAt(epochMillis: Long) = delayAt(epochMillis) * 60_000L
    /** Pending change still waiting at [epochMillis], if any. */
    fun waitingAt(epochMillis: Long) = pendingMinutes?.takeIf { pendingAtEpochMillis != null && epochMillis < pendingAtEpochMillis }

    /** The setting after asking for [minutes] at [epochMillis]: raising applies at once, lowering waits for the current delay. */
    fun request(minutes: Int, epochMillis: Long): StrictSetting {
        require(minutes in Strictness.DELAYS)
        val current = delayAt(epochMillis)
        return if (Strictness.loosensDelay(current, minutes)) StrictSetting(current, minutes, epochMillis + current * 60_000L)
        else StrictSetting(minutes)
    }

    /** Keeps the delay in force and drops a waiting decrease; this only tightens, so it never waits. */
    fun cancel(epochMillis: Long) = StrictSetting(delayAt(epochMillis))
}
