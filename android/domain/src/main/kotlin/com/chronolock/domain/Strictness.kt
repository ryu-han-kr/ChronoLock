package com.chronolock.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Strict mode delays every change that would let an app be used sooner, so an impulse cannot undo a limit at once.
 * Tightening still applies immediately.
 */
object Strictness {
    /** Delay choices in minutes; 0 turns strict mode off. Beyond [FreeTier.STRICT_MINUTES] they are Pro. */
    val DELAYS = listOf(0, 10, 30, 60, 360, 1440)

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
            lowered(current.pauseSeconds?.toLong(), next.pauseSeconds?.toLong()) ||
            raised(current.checkInMinutes?.toLong(), next.checkInMinutes?.toLong()) ||
            (next.pausedUntilEpochMillis ?: 0) > oldPause
    }

    /** Whether replacing [current] with [next] (null deletes the group) relaxes its shared limits. */
    fun loosens(current: AppGroup?, next: AppGroup?): Boolean {
        if (current == null || !current.enabled) return false
        if (next == null || !next.enabled) return true
        fun raised(old: Long?, new: Long?) = old != null && (new == null || new > old)
        return raised(current.dailyLimitSeconds, next.dailyLimitSeconds) ||
            raised(current.maxOpenCount?.toLong(), next.maxOpenCount?.toLong()) ||
            !next.schedules.containsAll(current.schedules) ||
            !next.members.keys.containsAll(current.members.keys)
    }

    /** Whether replacing [current] with [next] (null removes the site) lets the site open again. */
    fun loosens(current: BlockedSite?, next: BlockedSite?): Boolean = current?.enabled == true && next?.enabled != true

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

/**
 * Strict mode as stored: the delay in force, and a lower delay that takes over at [pendingAtEpochMillis] and, within the
 * boot [bootId] that asked for it, not before [pendingAtElapsedMillis], so moving the wall clock forward cannot hurry it.
 */
data class StrictSetting(
    val delayMinutes: Int = 0,
    val pendingMinutes: Int? = null,
    val pendingAtEpochMillis: Long? = null,
    val pendingAtElapsedMillis: Long? = null,
    val bootId: Int? = null,
) {
    /** By the wall clock alone; for showing the setting. Decisions use the [ClockReading] overloads. */
    fun delayAt(epochMillis: Long): Int =
        if (pendingMinutes != null && pendingAtEpochMillis != null && epochMillis >= pendingAtEpochMillis) pendingMinutes else delayMinutes
    fun delayMillisAt(epochMillis: Long) = delayAt(epochMillis) * 60_000L
    /** The wall clock and, within the same boot, elapsed time must both have reached the change; whichever is later wins. */
    fun delayAt(clock: ClockReading): Int {
        val due = pendingMinutes != null && pendingAtEpochMillis != null && clock.epochMillis >= pendingAtEpochMillis &&
            (bootId != clock.bootId || pendingAtElapsedMillis == null || clock.elapsedMillis >= pendingAtElapsedMillis)
        return if (due) pendingMinutes!! else delayMinutes
    }
    fun delayMillisAt(clock: ClockReading) = delayAt(clock) * 60_000L
    /** Pending change still waiting at [epochMillis], if any. */
    fun waitingAt(epochMillis: Long) = pendingMinutes?.takeIf { pendingAtEpochMillis != null && epochMillis < pendingAtEpochMillis }

    /** The setting after asking for [minutes] at [epochMillis]: raising applies at once, lowering waits for the current delay. */
    fun request(minutes: Int, epochMillis: Long): StrictSetting {
        require(minutes in Strictness.DELAYS)
        val current = delayAt(epochMillis)
        return if (Strictness.loosensDelay(current, minutes)) StrictSetting(current, minutes, epochMillis + current * 60_000L)
        else StrictSetting(minutes)
    }

    /** Like the wall-clock [request], but a lowering also waits on the boot clock of [clock]. */
    fun request(minutes: Int, clock: ClockReading): StrictSetting {
        require(minutes in Strictness.DELAYS)
        val current = delayAt(clock)
        return if (Strictness.loosensDelay(current, minutes)) StrictSetting(current, minutes, clock.epochMillis + current * 60_000L,
            clock.elapsedMillis + current * 60_000L, clock.bootId)
        else StrictSetting(minutes)
    }

    /** Keeps the delay in force and drops a waiting decrease; this only tightens, so it never waits. */
    fun cancel(epochMillis: Long) = StrictSetting(delayAt(epochMillis))
    fun cancel(clock: ClockReading) = StrictSetting(delayAt(clock))
}

/**
 * A self-imposed lock that is stronger than strict mode: while it holds, every change that would loosen a limit is
 * refused instead of delayed. A time lock ends at [untilEpochMillis] by itself; a goal lock ends only when the person
 * enters the password they chose, and only after [goalDays] days in a row within their limits since [goalStart].
 */
data class LockSetting(
    val untilEpochMillis: Long? = null,
    val goalDays: Int? = null,
    val goalStart: java.time.LocalDate? = null,
    /** True when the app made up the code and hands it out one digit per kept day, instead of the person's own password. */
    val pieceCode: Boolean = false,
    /** Digits handed out so far; the code is [goalDays] digits long. */
    val revealed: String = "",
    /** The same deadline on the boot clock of [bootId], so moving the wall clock forward cannot end a time lock early. */
    val untilElapsedMillis: Long? = null,
    val bootId: Int? = null,
) {
    fun timeLockedAt(epochMillis: Long) = untilEpochMillis?.let { epochMillis < it } == true
    /** Locked by the wall clock or, within the boot that set it, by elapsed time; whichever ends later wins. */
    fun timeLockedAt(clock: ClockReading) = timeLockedAt(clock.epochMillis) ||
        (bootId == clock.bootId && untilElapsedMillis?.let { clock.elapsedMillis < it } == true)
    fun lockedAt(clock: ClockReading) = timeLockedAt(clock) || goalLocked
    val goalLocked: Boolean get() = goalDays != null
    fun lockedAt(epochMillis: Long) = timeLockedAt(epochMillis) || goalLocked
    /** Kept days that count toward the goal: the streak, but never more than the days since the lock began. */
    fun goalProgress(keptStreak: Int, today: java.time.LocalDate): Int =
        goalStart?.let { minOf(keptStreak.toLong(), java.time.temporal.ChronoUnit.DAYS.between(it, today).coerceAtLeast(0)).toInt() } ?: 0
    fun goalReached(keptStreak: Int, today: java.time.LocalDate) = goalDays?.let { goalProgress(keptStreak, today) >= it } == true
    /** Whether another digit of a piece code can be handed out: one per kept day. */
    fun canReveal(keptStreak: Int, today: java.time.LocalDate) = pieceCode && revealed.length < goalProgress(keptStreak, today)

    companion object {
        /** Time lock lengths in hours, and goal lengths in days. */
        val HOURS = listOf(1, 6, 24, 72, 168)
        val GOAL_DAYS = listOf(3, 7, 14, 30)
    }
}
