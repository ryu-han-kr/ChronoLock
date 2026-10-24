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

private fun Set<DayOfWeek>.mask() = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
private fun daysOf(mask: Int) = DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

internal fun decodeSchedules(text: String): List<Schedule> = text.split(';').mapNotNull { part ->
    val (start, end, mask) = part.split('-').mapNotNull(String::toIntOrNull).takeIf { it.size == 3 } ?: return@mapNotNull null
    runCatching { Schedule(LocalTime.ofSecondOfDay(start * 60L), LocalTime.ofSecondOfDay(end * 60L), daysOf(mask)) }.getOrNull()
}

private fun List<Schedule>.encode() = takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.start.toSecondOfDay() / 60}-${it.end.toSecondOfDay() / 60}-${it.days.mask()}" }

fun RuleEntity.toDomain() = AppRule(
    packageName, appName, dailyLimitSeconds, maxOpenCount, cooldownSeconds, sessionLimitSeconds,
    schedules = schedules?.let(::decodeSchedules)
        ?: if (scheduleStartMinute != null && scheduleEndMinute != null && scheduleDaysMask != null) {
            listOf(Schedule(LocalTime.ofSecondOfDay(scheduleStartMinute * 60L), LocalTime.ofSecondOfDay(scheduleEndMinute * 60L), daysOf(scheduleDaysMask)))
        } else emptyList(),
    focusUntilEpochMillis = focusUntilEpochMillis, enabled = enabled, pausedUntilEpochMillis = pausedUntilEpochMillis,
    pauseSeconds = pauseSeconds, checkInMinutes = checkInMinutes,
)

fun AppRule.toEntity() = RuleEntity(packageName, appName, dailyLimitSeconds, maxOpenCount, cooldownSeconds, enabled,
    sessionLimitSeconds, focusUntilEpochMillis = focusUntilEpochMillis,
    schedules = schedules.encode(),
    pausedUntilEpochMillis = pausedUntilEpochMillis, pauseSeconds = pauseSeconds, checkInMinutes = checkInMinutes)

fun GroupEntity.toDomain() = AppGroup(id, name, members.split('\n').mapNotNull { line ->
    line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
}.toMap(), dailyLimitSeconds, maxOpenCount, enabled, schedules?.let(::decodeSchedules).orEmpty())

fun SiteEntity.toDomain() = BlockedSite(domain, enabled)
fun BlockedSite.toEntity() = SiteEntity(domain, enabled)

fun AppGroup.toEntity() = GroupEntity(id, name, members.entries.joinToString("\n") { "${it.key}\t${it.value.replace('\n', ' ').replace('\t', ' ')}" },
    dailyLimitSeconds, maxOpenCount, enabled, schedules.encode())

@Singleton
class LocalRepository @Inject constructor(private val database: ChronoDatabase) : RuleRepository {
    private val dao = database.dao()
    private val mutex = Mutex()
    private val monitor = UsageMonitor()
    private var state: MonitorState? = null
    val rules = dao.observeRules()
    val daily = dao.observeDaily()

    suspend fun recoveryStartEpochMillis(): Long? = mutex.withLock { dao.checkpoint()?.epochMillis }

    val pending = dao.observePending()

    override suspend fun saveRule(rule: AppRule) { requestRule(rule, 0, System.currentTimeMillis()) }

    /**
     * Saves [rule] now, or, when it relaxes the current rule and strict mode asks for [delayMillis], holds it until then.
     * Returns the time a held change will apply, or null when it was saved at once.
     */
    suspend fun requestRule(rule: AppRule, delayMillis: Long, nowEpochMillis: Long): Long? = mutex.withLock {
        database.withTransaction {
            val current = dao.rules().firstOrNull { it.packageName == rule.packageName }
            // Focus and pauses belong to the running session, not to an editor snapshot opened earlier.
            val next = rule.copy(focusUntilEpochMillis = current?.focusUntilEpochMillis, pausedUntilEpochMillis = current?.pausedUntilEpochMillis)
            change(rule.packageName, current, next, delayMillis, nowEpochMillis)
        }
    }

    suspend fun requestDelete(packageName: String, delayMillis: Long, nowEpochMillis: Long): Long? = mutex.withLock {
        database.withTransaction { change(packageName, dao.rules().firstOrNull { it.packageName == packageName }, null, delayMillis, nowEpochMillis) }
    }

    /** Pauses a rule for [minutes], or until midnight when null. Under strict mode the pause starts once the delay has passed. */
    suspend fun requestPause(packageName: String, minutes: Int?, delayMillis: Long, nowEpochMillis: Long, zone: ZoneId): Long? = mutex.withLock {
        database.withTransaction {
            val current = requireNotNull(dao.rules().firstOrNull { it.packageName == packageName })
            val until = Strictness.pauseEnd(nowEpochMillis + delayMillis.coerceAtLeast(0), minutes, zone)
            change(packageName, current, current.toDomain().copy(pausedUntilEpochMillis = until), delayMillis, nowEpochMillis)
        }
    }

    /** Ends a pause and drops any change still waiting for this app; both only tighten, so they never wait. */
    suspend fun resume(packageName: String, nowEpochMillis: Long) = mutex.withLock {
        database.withTransaction {
            val current = dao.rules().firstOrNull { it.packageName == packageName }
            if (current != null) change(packageName, current, current.toDomain().copy(pausedUntilEpochMillis = null), 0, nowEpochMillis)
            else dao.deletePending(packageName)
        }
    }

    suspend fun cancelPending(packageName: String) = mutex.withLock { dao.deletePending(packageName) }

    /** Drops every held relaxation (rules, groups, sites); dropping only tightens, so a lock starts with none waiting. */
    suspend fun cancelAllPending() = mutex.withLock {
        database.withTransaction { dao.clearPending(); dao.clearPendingGroups(); dao.clearPendingSites() }
    }

    /** Applies held changes whose time has come; the monitor calls this on every evaluation, the screen when it opens. */
    suspend fun applyDuePending(nowEpochMillis: Long) = mutex.withLock { applyDue(nowEpochMillis) }

    val groups = dao.observeGroups()
    val pendingGroups = dao.observePendingGroups()

    /**
     * Saves a new or edited group, or holds a relaxing edit like [requestRule]. A new group only adds limits, so it never waits.
     * Returns the time a held change will apply, or null when it was saved at once.
     */
    suspend fun requestGroup(group: AppGroup, delayMillis: Long, nowEpochMillis: Long): Long? = mutex.withLock {
        database.withTransaction {
            val all = dao.groups()
            val current = all.firstOrNull { it.id == group.id }?.toDomain()
            // One group per app; overlaps saved before that rule may stay, but no new app may join a second group.
            val taken = all.map { it.toDomain() }.takenBy(group.id).keys - current?.members?.keys.orEmpty()
            require(group.members.keys.none { it in taken }) { GROUP_TAKEN }
            if (group.id == 0L) {
                dao.insertGroup(group.toEntity())
                dao.insertEvents(listOf(EventEntity(type = "GROUP_UPDATED", packageName = null, epochMillis = nowEpochMillis)))
                return@withTransaction null
            }
            changeGroup(group.id, current, group, delayMillis, nowEpochMillis)
        }
    }

    companion object {
        const val GROUP_TAKEN = "다른 그룹에 들어 있는 앱은 함께 넣을 수 없습니다."
        const val SITES_FULL = "차단할 사이트는 최대 50개까지 추가할 수 있습니다."
        /** Passed as the delay while a lock holds: loosening changes are refused, tightening ones apply at once. */
        const val LOCKED_DELAY = -1L
        const val IDLE_WRITE_MS = 15_000L
        const val LOCKED = "잠금 중에는 제한을 풀거나 느슨하게 바꿀 수 없습니다."
    }

    suspend fun requestDeleteGroup(id: Long, delayMillis: Long, nowEpochMillis: Long): Long? = mutex.withLock {
        database.withTransaction { changeGroup(id, dao.groups().firstOrNull { it.id == id }?.toDomain(), null, delayMillis, nowEpochMillis) }
    }

    suspend fun cancelPendingGroup(id: Long) = mutex.withLock { dao.deletePendingGroup(id) }

    private suspend fun changeGroup(id: Long, current: AppGroup?, next: AppGroup?, delayMillis: Long, nowEpochMillis: Long): Long? {
        val loosens = Strictness.loosens(current, next)
        if (delayMillis == LOCKED_DELAY && loosens) throw IllegalStateException(LOCKED)
        if (delayMillis > 0 && loosens) {
            val applyAt = Math.addExact(nowEpochMillis, delayMillis)
            dao.savePendingGroup(PendingGroupEntity(id, applyAt, next == null, (next ?: current!!).toEntity()))
            return applyAt
        }
        dao.deletePendingGroup(id)
        if (next != null || current != null) writeGroup(id, next, nowEpochMillis)
        return null
    }

    private suspend fun writeGroup(id: Long, group: AppGroup?, epochMillis: Long) {
        if (group == null) dao.deleteGroup(id) else dao.saveGroup(group.toEntity())
        dao.insertEvents(listOf(EventEntity(type = if (group == null) "GROUP_DELETED" else "GROUP_UPDATED", packageName = null, epochMillis = epochMillis)))
    }

    val sites = dao.observeSites()
    val pendingSites = dao.observePendingSites()

    /** Adds, re-enables or disables a site; disabling under strict mode waits like [requestRule]. */
    suspend fun requestSite(site: BlockedSite, delayMillis: Long, nowEpochMillis: Long): Long? = mutex.withLock {
        database.withTransaction {
            val all = dao.sites()
            val current = all.firstOrNull { it.domain == site.domain }?.toDomain()
            require(current != null || all.size < SiteDomains.MAX_SITES) { SITES_FULL }
            changeSite(site.domain, current, site, delayMillis, nowEpochMillis)
        }
    }

    suspend fun requestDeleteSite(domain: String, delayMillis: Long, nowEpochMillis: Long): Long? = mutex.withLock {
        database.withTransaction { changeSite(domain, dao.sites().firstOrNull { it.domain == domain }?.toDomain(), null, delayMillis, nowEpochMillis) }
    }

    suspend fun cancelPendingSite(domain: String) = mutex.withLock { dao.deletePendingSite(domain) }

    /** Enabled sites after applying held changes whose time has come; the accessibility service checks addresses against these. */
    suspend fun blockedSites(nowEpochMillis: Long): List<BlockedSite> = mutex.withLock {
        applyDue(nowEpochMillis)
        dao.sites().map { it.toDomain() }.filter { it.enabled }
    }

    private suspend fun changeSite(domain: String, current: BlockedSite?, next: BlockedSite?, delayMillis: Long, nowEpochMillis: Long): Long? {
        val loosens = Strictness.loosens(current, next)
        if (delayMillis == LOCKED_DELAY && loosens) throw IllegalStateException(LOCKED)
        if (delayMillis > 0 && loosens) {
            val applyAt = Math.addExact(nowEpochMillis, delayMillis)
            dao.savePendingSite(PendingSiteEntity(domain, applyAt, next == null, (next ?: current!!).toEntity()))
            return applyAt
        }
        dao.deletePendingSite(domain)
        if (next == null) dao.deleteSite(domain) else dao.saveSite(next.toEntity())
        return null
    }

    private suspend fun applyDue(nowEpochMillis: Long) {
        dao.duePendingSites(nowEpochMillis).takeIf { it.isNotEmpty() }?.let { due ->
            database.withTransaction {
                due.forEach { held ->
                    // A site removed meanwhile stays removed; the held change has nothing left to unblock.
                    if (held.remove) dao.deleteSite(held.domain)
                    else if (dao.sites().any { it.domain == held.domain }) dao.saveSite(held.site)
                    dao.deletePendingSite(held.domain)
                }
            }
        }
        dao.duePendingGroups(nowEpochMillis).takeIf { it.isNotEmpty() }?.let { due ->
            database.withTransaction {
                due.forEach { held ->
                    // A group deleted meanwhile stays deleted; the held edit has nothing left to relax.
                    if (held.remove) writeGroup(held.groupId, null, held.applyAtEpochMillis)
                    else if (dao.groups().any { it.id == held.groupId }) writeGroup(held.groupId, held.group.toDomain(), held.applyAtEpochMillis)
                    dao.deletePendingGroup(held.groupId)
                }
            }
        }
        val due = dao.duePending(nowEpochMillis).takeIf { it.isNotEmpty() } ?: return
        database.withTransaction {
            due.forEach { held ->
                val current = dao.rules().firstOrNull { it.packageName == held.packageName }
                if (held.remove) writeRule(held.packageName, null, held.applyAtEpochMillis)
                else writeRule(held.packageName, held.rule.toDomain().copy(focusUntilEpochMillis = current?.focusUntilEpochMillis), held.applyAtEpochMillis)
                dao.deletePending(held.packageName)
            }
        }
    }

    private suspend fun change(packageName: String, current: RuleEntity?, next: AppRule?, delayMillis: Long, nowEpochMillis: Long): Long? {
        val loosens = Strictness.loosens(current?.toDomain(), next, nowEpochMillis)
        if (delayMillis == LOCKED_DELAY && loosens) throw IllegalStateException(LOCKED)
        if (delayMillis > 0 && loosens) {
            val applyAt = Math.addExact(nowEpochMillis, delayMillis)
            dao.savePending(PendingRuleEntity(packageName, applyAt, next == null, (next ?: current!!.toDomain()).toEntity()))
            return applyAt
        }
        // The latest decision replaces one still waiting; dropping a held relaxation only tightens.
        dao.deletePending(packageName)
        if (next != null || current != null) writeRule(packageName, next, nowEpochMillis)
        return null
    }

    private suspend fun writeRule(packageName: String, rule: AppRule?, epochMillis: Long) {
        if (rule == null) dao.deleteRule(packageName) else dao.saveRule(rule.toEntity())
        dao.insertEvents(listOf(EventEntity(type = if (rule == null) "RULE_DISABLED" else "RULE_UPDATED", packageName = packageName, epochMillis = epochMillis)))
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

    override suspend fun deleteRule(packageName: String) { requestDelete(packageName, 0, System.currentTimeMillis()) }

    suspend fun reconnect(now: ClockReading, recoveredUsage: Map<String, Long>, foregroundPackage: String? = null): MonitorState = mutex.withLock {
        val loaded = load()
        val update = monitor.restore(loaded, now, recoveredUsage, foregroundPackage, ruleMap(now))
        persist(update)
        update.state
    }

    suspend fun foreground(packageName: String?, now: ClockReading): MonitorState = mutex.withLock {
        val rules = ruleMap(now)
        val update = monitor.foreground(state ?: load(), packageName, now, rules)
        persist(update, tracked = packageName in rules)
        update.state
    }

    suspend fun tick(now: ClockReading): MonitorState = mutex.withLock {
        val rules = ruleMap(now)
        val update = monitor.tick(state ?: load(), now, rules)
        persist(update, tracked = update.state.observedPackage in rules)
        update.state
    }

    /** Replaces every rule, group and site, as a restore does; usage history and the monitor state stay. */
    suspend fun replaceAll(rules: List<AppRule>, groups: List<AppGroup>, sites: List<BlockedSite>, nowEpochMillis: Long) = mutex.withLock {
        database.withTransaction {
            dao.clearRules(); dao.clearPending(); dao.clearGroups(); dao.clearPendingGroups(); dao.clearSites(); dao.clearPendingSites()
            sites.take(SiteDomains.MAX_SITES).forEach { dao.saveSite(it.toEntity()) }
            rules.forEach { dao.saveRule(it.copy(focusUntilEpochMillis = null, pausedUntilEpochMillis = null).toEntity()) }
            groups.forEach { dao.insertGroup(it.copy(id = 0).toEntity()) }
            dao.insertEvents(listOf(EventEntity(type = "BACKUP_RESTORED", packageName = null, epochMillis = nowEpochMillis)))
        }
    }

    /**
     * Removes every rule, group and blocked site (and changes strict mode was holding back). Usage history and today's
     * counts stay, so resetting cannot be used to clear what was used; history is deleted separately behind a password.
     */
    suspend fun resetRules(nowEpochMillis: Long) = mutex.withLock {
        database.withTransaction {
            dao.clearRules(); dao.clearPending(); dao.clearGroups(); dao.clearPendingGroups(); dao.clearSites(); dao.clearPendingSites()
            dao.insertEvents(listOf(EventEntity(type = "RULES_RESET", packageName = null, epochMillis = nowEpochMillis)))
        }
    }

    private suspend fun ruleMap(now: ClockReading): Map<String, AppRule> {
        applyDue(now.epochMillis)
        return withGroups(dao.rules().associate { it.packageName to it.toDomain() }, dao.groups().map { it.toDomain() })
    }

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

    /** The state last written to the database and when; writes are diffed against it. */
    private var written: MonitorState? = null
    private var writtenAt = 0L

    /**
     * Stores the monitor state. While no limited app is on screen and nothing else changed, the per-second checkpoint is
     * written at most every [IDLE_WRITE_MS]; recovery fills a short gap from usage events after a crash.
     */
    private suspend fun persist(update: MonitorUpdate, tracked: Boolean = true) {
        val clock = update.state.clock ?: return
        val retained = update.state.copy(daily = update.state.daily.filterKeys { it.date >= clock.date.minusDays(30) })
        val previous = written
        val quiet = !tracked && update.events.isEmpty() && previous != null && previous.clock?.date == clock.date &&
            previous.observedPackage == retained.observedPackage && previous.active?.packageName == retained.active?.packageName &&
            previous.blockedSession?.packageName == retained.blockedSession?.packageName && previous.decision.denied == retained.decision.denied &&
            clock.epochMillis - writtenAt in 0 until IDLE_WRITE_MS
        if (quiet) { state = retained; return }
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
        written = retained
        writtenAt = clock.epochMillis
    }
}
