package com.chronolock.data

import androidx.room.withTransaction
import com.chronolock.domain.*
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun RuleEntity.toDomain() = AppRule(packageName, appName, dailyLimitSeconds, maxOpenCount, cooldownSeconds, enabled = enabled)

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
            dao.saveRule(RuleEntity(rule.packageName, rule.appName, rule.dailyLimitSeconds, rule.maxOpenCount, rule.cooldownSeconds, rule.enabled))
            dao.insertEvents(listOf(EventEntity(type = "RULE_UPDATED", packageName = rule.packageName, epochMillis = System.currentTimeMillis())))
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
            active = checkpoint?.activePackage?.let { ActiveSession(it, checkpoint.sessionMillis) },
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
                epochMillis = clock.epochMillis, elapsedMillis = clock.elapsedMillis, bootId = clock.bootId, zone = clock.zone.id))
            dao.insertEvents(update.events.map { EventEntity(type = it.type, packageName = it.packageName, epochMillis = it.epochMillis) })
            if (previous?.clock?.date != clock.date) {
                dao.pruneEvents(clock.epochMillis - 30L * 86400 * 1000)
                dao.pruneDaily(clock.date.minusDays(30).toString())
            }
        }
        state = retained
    }
}
