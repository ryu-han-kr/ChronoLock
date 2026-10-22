package com.chronolock.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ClockReading(val epochMillis: Long, val elapsedMillis: Long, val bootId: Int, val zone: ZoneId) {
    val date: LocalDate get() = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
data class DailyKey(val packageName: String, val date: LocalDate)
data class DailyUsage(val usageMillis: Long = 0, val opens: Int = 0, val blockedAttempts: Int = 0)
data class CloseMark(val epochMillis: Long, val elapsedMillis: Long, val bootId: Int) {
    fun age(now: ClockReading): Long = if (bootId == now.bootId && now.elapsedMillis >= elapsedMillis) {
        now.elapsedMillis - elapsedMillis
    } else (now.epochMillis - epochMillis).coerceAtLeast(0)
}
data class ActiveSession(val packageName: String, val usageMillis: Long = 0)
data class MonitorState(
    val observedPackage: String? = null,
    val active: ActiveSession? = null,
    val clock: ClockReading? = null,
    val daily: Map<DailyKey, DailyUsage> = emptyMap(),
    val lastClosed: Map<String, CloseMark> = emptyMap(),
    val decision: RuleDecision = RuleDecision.Allow,
    // Preserve the completed session's usage while its blocking overlay is still visible.
    val blockedSession: ActiveSession? = null,
)
data class UsageEvent(val type: String, val packageName: String?, val epochMillis: Long)
data class MonitorUpdate(val state: MonitorState, val events: List<UsageEvent> = emptyList())

/** Pure state machine. Its owner serializes calls and persists each update atomically. */
class UsageMonitor(private val engine: RuleEngine = RuleEngine()) {
    fun foreground(state: MonitorState, target: String?, now: ClockReading, rules: Map<String, AppRule>): MonitorUpdate {
        if (state.observedPackage == target) return tick(state, now, rules)
        val measured = measure(state, now)
        val events = mutableListOf<UsageEvent>()
        var next = close(measured, now, events).copy(observedPackage = target, decision = RuleDecision.Allow, blockedSession = null)
        if (target != null && rules[target]?.enabled == true) {
            val decision = evaluate(next, target, now, rules)
            if (decision.denied) {
                next = incrementBlocked(next, target, now).copy(decision = decision)
                events += UsageEvent("BLOCK_TRIGGERED", target, now.epochMillis)
            } else {
                next = open(next, target, now, events)
            }
        }
        return MonitorUpdate(next, events)
    }

    fun tick(state: MonitorState, now: ClockReading, rules: Map<String, AppRule>): MonitorUpdate {
        var next = measure(state, now)
        val events = mutableListOf<UsageEvent>()
        val target = next.observedPackage ?: return MonitorUpdate(next)
        val decision = evaluate(next, target, now, rules)
        if (decision.denied) {
            if (!next.decision.denied) {
                next = next.copy(blockedSession = next.active ?: next.blockedSession)
                next = close(next, now, events)
                next = incrementBlocked(next, target, now)
                events += UsageEvent("BLOCK_TRIGGERED", target, now.epochMillis)
            }
            next = next.copy(decision = decision)
        } else {
            if (next.active == null && rules[target]?.enabled == true) next = open(next, target, now, events)
            if (rules[target]?.enabled != true) next = close(next, now, events)
            next = next.copy(decision = RuleDecision.Allow, blockedSession = null)
        }
        return MonitorUpdate(next, events)
    }

    /** Never bill service downtime as if continuous foreground usage had been observed. */
    fun reconnect(state: MonitorState, now: ClockReading): MonitorUpdate {
        val events = mutableListOf<UsageEvent>()
        val closed = close(state, state.clock ?: now, events)
        events += UsageEvent("MONITORING_RESUMED", null, now.epochMillis)
        return MonitorUpdate(closed.copy(observedPackage = null, clock = now, decision = RuleDecision.Allow, blockedSession = null), events)
    }

    /** recoveredUsage contains today's milliseconds after the durable checkpoint, not full-day totals. */
    fun restore(
        loaded: MonitorState,
        now: ClockReading,
        recoveredUsage: Map<String, Long>,
        foregroundPackage: String?,
        rules: Map<String, AppRule>,
    ): MonitorUpdate {
        val sameBoot = loaded.clock?.let { it.bootId == now.bootId && it.elapsedMillis <= now.elapsedMillis } == true
        val sameForeground = sameBoot && foregroundPackage != null &&
            (loaded.observedPackage == foregroundPackage || loaded.active?.packageName == foregroundPackage)
        val daily = loaded.daily.toMutableMap()
        recoveredUsage.filterKeys { rules[it]?.enabled == true }.forEach { (app, usage) ->
            // Recovery runs only in a new service instance, and the blocking overlay dies with the old one. Usage after the
            // checkpoint (written every second while the screen is on) was therefore real use without the overlay, so it counts
            // even for an app that was blocked; at most the last tick's second overlaps the overlay.
            val key = DailyKey(app, now.date)
            val existing = daily[key] ?: DailyUsage()
            daily[key] = existing.copy(usageMillis = existing.usageMillis + usage.coerceAtLeast(0))
        }
        return if (sameForeground && rules[foregroundPackage]?.enabled == true) {
            val active = loaded.active?.let {
                it.copy(usageMillis = it.usageMillis + (recoveredUsage[it.packageName] ?: 0).coerceAtLeast(0))
            }
            val restored = tick(loaded.copy(active = active, daily = daily, clock = now, observedPackage = foregroundPackage), now, rules)
            restored.copy(events = listOf(UsageEvent("MONITORING_RESUMED", null, now.epochMillis)) + restored.events)
        } else {
            val resumed = reconnect(loaded.copy(daily = daily), now)
            val opened = foreground(resumed.state, foregroundPackage, now, rules)
            opened.copy(events = resumed.events + opened.events)
        }
    }

    private fun evaluate(state: MonitorState, target: String, now: ClockReading, rules: Map<String, AppRule>): RuleDecision {
        val daily = state.daily[DailyKey(target, now.date)] ?: DailyUsage()
        return engine.evaluate(RuleContext(
            now.epochMillis, now.zone, daily.usageMillis, state.active?.usageMillis ?: state.blockedSession?.usageMillis ?: 0,
            daily.opens, state.active == null, state.lastClosed[target]?.age(now),
        ), rules[target])
    }

    private fun open(state: MonitorState, target: String, now: ClockReading, events: MutableList<UsageEvent>): MonitorState {
        val key = DailyKey(target, now.date)
        val daily = state.daily[key] ?: DailyUsage()
        events += UsageEvent("APP_OPEN", target, now.epochMillis)
        return state.copy(active = ActiveSession(target), daily = state.daily + (key to daily.copy(opens = daily.opens + 1)))
    }

    private fun close(state: MonitorState, now: ClockReading, events: MutableList<UsageEvent>): MonitorState {
        val active = state.active ?: return state
        events += UsageEvent("APP_CLOSE", active.packageName, now.epochMillis)
        return state.copy(active = null, lastClosed = state.lastClosed +
            (active.packageName to CloseMark(now.epochMillis, now.elapsedMillis, now.bootId)))
    }

    private fun incrementBlocked(state: MonitorState, target: String, now: ClockReading): MonitorState {
        val key = DailyKey(target, now.date)
        val value = state.daily[key] ?: DailyUsage()
        return state.copy(daily = state.daily + (key to value.copy(blockedAttempts = value.blockedAttempts + 1)))
    }

    private fun measure(state: MonitorState, now: ClockReading): MonitorState {
        val previous = state.clock
        val active = state.active
        if (previous == null || active == null) return state.copy(clock = now)
        val delta = if (previous.bootId == now.bootId) (now.elapsedMillis - previous.elapsedMillis).coerceAtLeast(0) else 0
        if (delta == 0L) return state.copy(clock = now)
        val daily = state.daily.toMutableMap()
        // Distribute measured elapsed duration along the corresponding wall-time interval.
        // On a clock/zone change, attribute the interval to the current date rather than fabricating history.
        val wallDelta = now.epochMillis - previous.epochMillis
        if (previous.zone != now.zone || kotlin.math.abs(wallDelta - delta) > 2000) {
            addUsage(daily, DailyKey(active.packageName, now.date), delta)
        } else {
            var cursor = now.epochMillis - delta
            while (cursor < now.epochMillis) {
                val date = Instant.ofEpochMilli(cursor).atZone(now.zone).toLocalDate()
                val midnight = date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
                val end = minOf(midnight, now.epochMillis)
                addUsage(daily, DailyKey(active.packageName, date), end - cursor)
                cursor = end
            }
        }
        return state.copy(clock = now, daily = daily, active = active.copy(usageMillis = active.usageMillis + delta))
    }

    private fun addUsage(daily: MutableMap<DailyKey, DailyUsage>, key: DailyKey, delta: Long) {
        val value = daily[key] ?: DailyUsage()
        daily[key] = value.copy(usageMillis = value.usageMillis + delta)
    }
}
