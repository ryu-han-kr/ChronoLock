package com.chronolock.data

import androidx.room.withTransaction
import com.chronolock.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun RuleEntity.toDomain() = AppRule(
    packageName, appName, dailyLimitSeconds, maxOpenCount, cooldownSeconds, sessionLimitSeconds,
    schedule = if (scheduleStartMinute != null && scheduleEndMinute != null && scheduleDaysMask != null) {
        Schedule(LocalTime.ofSecondOfDay(scheduleStartMinute * 60L), LocalTime.ofSecondOfDay(scheduleEndMinute * 60L),
            DayOfWeek.values().filter { scheduleDaysMask and (1 shl (it.value - 1)) != 0 }.toSet())
    } else null,
    focusUntilEpochMillis = focusUntilEpochMillis, enabled = enabled,
)

fun AppRule.toEntity() = RuleEntity(packageName, appName, dailyLimitSeconds, maxOpenCount, cooldownSeconds, enabled,
    sessionLimitSeconds, schedule?.start?.toSecondOfDay()?.div(60), schedule?.end?.toSecondOfDay()?.div(60),
    schedule?.days?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }, focusUntilEpochMillis)

@Singleton
class LocalRepository @Inject constructor(private val database: ChronoDatabase) : RuleRepository {
    private val dao = database.dao()
    private val mutex = Mutex()
    private val monitor = UsageMonitor()
    private var state: MonitorState? = null
    val rules = dao.observeRules()
    val daily = dao.observeDaily()

    suspend fun recoveryStartEpochMillis(): Long? = mutex.withLock { dao.checkpoint()?.epochMillis }

    override suspend fun saveRule(rule: AppRule) = mutex.withLock {
        database.withTransaction {
            // Focus belongs to the active session, not to an editor snapshot opened earlier.
            val currentFocus = dao.rules().firstOrNull { it.packageName == rule.packageName }?.focusUntilEpochMillis
            dao.saveRule(rule.copy(focusUntilEpochMillis = currentFocus).toEntity())
            dao.insertEvents(listOf(EventEntity(type = "RULE_UPDATED", packageName = rule.packageName, epochMillis = System.currentTimeMillis())))
        }
    }

    suspend fun startFocus(minutes: Int, nowEpochMillis: Long) = mutex.withLock {
        require(minutes in 1..180) { "집중 시간은 1~180분으로 설정해 주세요." }
        require(dao.rules().any { it.enabled }) { "먼저 집중할 때 제한할 앱을 설정해 주세요." }
        val until = Math.addExact(nowEpochMillis, minutes * 60000L)
        database.withTransaction {
            dao.startFocus(until)
            dao.insertEvents(listOf(EventEntity(type = "FOCUS_STARTED", packageName = null, epochMillis = nowEpochMillis)))
        }
    }

    suspend fun stopFocus(nowEpochMillis: Long) = mutex.withLock {
        database.withTransaction {
            dao.stopFocus()
            dao.insertEvents(listOf(EventEntity(type = "FOCUS_CANCELLED", packageName = null, epochMillis = nowEpochMillis)))
        }
    }

    override suspend fun deleteRule(packageName: String) = mutex.withLock {
        database.withTransaction {
            dao.deleteRule(packageName)
            dao.insertEvents(listOf(EventEntity(type = "RULE_DISABLED", packageName = packageName, epochMillis = System.currentTimeMillis())))
        }
    }

    suspend fun reconnect(now: ClockReading, recoveredUsage: Map<String, Long>, foregroundPackage: String? = null): MonitorState = mutex.withLock {
        val loaded = load()
        val update = monitor.restore(loaded, now, recoveredUsage, foregroundPackage, ruleMap())
        persist(update)
        update.state
    }

    suspend fun foreground(packageName: String?, now: ClockReading): MonitorState = mutex.withLock {
        val update = monitor.foreground(state ?: load(), packageName, now, ruleMap())
        persist(update)
        update.state
    }

    suspend fun tick(now: ClockReading): MonitorState = mutex.withLock {
        val update = monitor.tick(state ?: load(), now, ruleMap())
        persist(update)
        update.state
    }

    suspend fun clearAll() = mutex.withLock {
        database.withTransaction {
            dao.clearRules(); dao.clearDaily(); dao.clearCloses(); dao.clearCheckpoint(); dao.clearEvents()
        }
        state = MonitorState()
    }

    private suspend fun ruleMap() = dao.rules().associate { it.packageName to it.toDomain() }

    private suspend fun load(): MonitorState {
        val checkpoint = dao.checkpoint()
        return MonitorState(
            observedPackage = checkpoint?.observedPackage,
            active = checkpoint?.activePackage?.let { ActiveSession(it, checkpoint.sessionMillis) },
            blockedSession = checkpoint?.blockedPackage?.let { ActiveSession(it, checkpoint.blockedSessionMillis ?: 0) },
            decision = if (checkpoint?.wasBlocked == true) RuleDecision(DecisionType.BLOCK) else RuleDecision.Allow,
            clock = checkpoint?.let { ClockReading(it.epochMillis, it.elapsedMillis, it.bootId, ZoneId.of(it.zone)) },
            daily = dao.daily().associate { DailyKey(it.packageName, LocalDate.parse(it.date)) to DailyUsage(it.usageMillis, it.opens, it.blockedAttempts) },
            lastClosed = dao.closes().associate { it.packageName to CloseMark(it.epochMillis, it.elapsedMillis, it.bootId) },
        )
    }

    private suspend fun persist(update: MonitorUpdate) {
        val clock = update.state.clock ?: return
        val retained = update.state.copy(daily = update.state.daily.filterKeys { it.date >= clock.date.minusDays(30) })
        val previous = state
        database.withTransaction {
            dao.saveDaily(retained.daily.filter { (key, value) -> previous?.daily?.get(key) != value }.map { (key, value) ->
                DailyEntity(key.packageName, key.date.toString(), value.usageMillis, value.opens, value.blockedAttempts)
            })
            dao.saveCloses(retained.lastClosed.filter { (key, value) -> previous?.lastClosed?.get(key) != value }.map { (app, mark) ->
                CloseEntity(app, mark.epochMillis, mark.elapsedMillis, mark.bootId)
            })
            dao.saveCheckpoint(CheckpointEntity(activePackage = retained.active?.packageName, sessionMillis = retained.active?.usageMillis ?: 0,
                epochMillis = clock.epochMillis, elapsedMillis = clock.elapsedMillis, bootId = clock.bootId, zone = clock.zone.id,
                observedPackage = retained.observedPackage, blockedPackage = retained.blockedSession?.packageName,
                blockedSessionMillis = retained.blockedSession?.usageMillis, wasBlocked = retained.decision.denied))
            dao.insertEvents(update.events.map { EventEntity(type = it.type, packageName = it.packageName, epochMillis = it.epochMillis) })
            if (previous?.clock?.date != clock.date) {
                dao.pruneEvents(clock.epochMillis - 30L * 86400 * 1000)
                dao.pruneDaily(clock.date.minusDays(30).toString())
            }
        }
        state = retained
    }
}
