package com.chronolock.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.room.withTransaction
import com.chronolock.blocking.AndroidEnvironment
import com.chronolock.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class HistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ChronoDatabase,
    private val environment: AndroidEnvironment,
) {
    private val dao = database.historyDao()
    private val mutex = Mutex()
    val daily = dao.observeDaily()
    val meta = dao.observeMeta()
    val estimates = dao.observeEstimates()

    val year = dao.observeYear()

    /**
     * Import overlapping OS events idempotently. Android keeps detailed events for about a week, so raw events stay
     * [EVENT_DAYS] days while the per-app daily rows built from them stay [RETAIN_DAYS] days and day totals a year.
     */
    @Suppress("DEPRECATION")
    suspend fun collect() = mutex.withLock { withContext(Dispatchers.IO) {
        if (!environment.hasUsageAccess()) return@withContext
        check(context.getSystemService(UserManager::class.java).isUserUnlocked)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val previous = dao.meta() ?: HistoryMetaEntity()
        val queryFrom = maxOf(now - EVENT_DAYS * DAY, previous.clearedAt,
            if (previous.collectedAt > 0 && previous.collectedAt <= now) previous.collectedAt - 86_400_000L else 0)
        val stream = checkNotNull(context.getSystemService(UsageStatsManager::class.java).queryEvents(queryFrom, now))
        val collected = mutableListOf<HistoryEventEntity>()
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> HistoryEventType.RESUME
                UsageEvents.Event.ACTIVITY_PAUSED -> HistoryEventType.PAUSE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> HistoryEventType.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN -> HistoryEventType.SHUTDOWN
                UsageEvents.Event.DEVICE_STARTUP -> HistoryEventType.STARTUP
                else -> null
            } ?: continue
            collected += HistoryEventEntity(at = event.timeStamp, type = type.name,
                app = event.packageName.orEmpty(), activity = event.className.orEmpty())
        }
        val pm = context.packageManager
        val names = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        importEvents(collected, names, homes, now, zone)
        // Older days are not estimated from the OS's coarse usage buckets: history shows recorded days only.
        dao.clearEstimates()
        refreshYear(now, zone)
    } }

    /** Copies each retained day's total into the year table, then drops what is older than a year. */
    internal suspend fun refreshYear(now: Long, zone: ZoneId) {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        database.withTransaction {
            val recorded = dao.daily().groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.usageMillis } }
            dao.saveYear(recorded.map { (date, millis) -> HistoryYearEntity(date, millis, false) })
            dao.pruneYear(today.minusDays(YEAR_DAYS - 1L).toString())
        }
    }

    /** Deletes every kept record and day total; nothing from before now is imported again. Limits are not affected. */
    suspend fun clearHistory(now: Long) = mutex.withLock {
        database.withTransaction {
            dao.clearDaily(); dao.clearEvents(); dao.clearEstimates(); dao.clearYear()
            dao.saveMeta((dao.meta() ?: HistoryMetaEntity()).copy(clearedAt = now))
        }
    }

    companion object {
        /** Per-app day rows are kept a year; each refresh drops the days that fell out of it, oldest first. */
        const val RETAIN_DAYS = 365
        const val EVENT_DAYS = 10
        const val YEAR_DAYS = 366
        private const val DAY = 86_400_000L
    }

    /**
     * Stores per-day estimates for the window days before the first event record and after the last reset.
     * Days that can no longer be estimated (the OS dropped the bucket) keep their earlier estimate.
     */
    internal suspend fun importEstimates(bucketsByInterval: List<List<UsageBucket>>, names: Map<String, String>,
        homes: Set<String>, now: Long, zone: ZoneId) {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        database.withTransaction {
            val meta = dao.meta() ?: HistoryMetaEntity()
            val daily = dao.daily()
            val windowStart = today.minusDays(RETAIN_DAYS - 1L)
            val afterReset = if (meta.clearedAt > 0) java.time.Instant.ofEpochMilli(meta.clearedAt).atZone(zone).toLocalDate().plusDays(1) else windowStart
            val gapStart = maxOf(windowStart, afterReset)
            val gapEnd = daily.minOfOrNull { LocalDate.parse(it.date) } ?: today
            val labels = dao.estimates().associate { it.packageName to it.appName } + daily.associate { it.packageName to it.appName } + names
            val recorded = daily.groupBy { it.packageName }.mapValues { (_, rows) -> rows.associate { LocalDate.parse(it.date) to it.usageMillis } }
            val estimated = estimateGapUsage(bucketsByInterval.map { buckets -> buckets.filter { it.app in labels && it.app !in homes } },
                recorded, gapStart, gapEnd, zone)
            dao.dropEstimates(from = gapEnd.toString(), before = gapStart.toString())
            dao.saveEstimates(estimated.map { (key, millis) ->
                HistoryEstimateEntity(key.packageName, key.date.toString(), labels.getValue(key.packageName), millis)
            })
        }
    }

    internal suspend fun importEvents(collected: List<HistoryEventEntity>, names: Map<String, String>,
        homes: Set<String>, now: Long, zone: ZoneId) {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val from = today.minusDays(RETAIN_DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        database.withTransaction {
            // Read again inside the transaction: deletion must never re-import pre-deletion records.
            val meta = dao.meta() ?: HistoryMetaEntity()
            val cutoff = maxOf(from, meta.clearedAt)
            val labels = dao.daily().associate { it.packageName to it.appName } + names
            dao.insertEvents(collected.filter { it.at >= meta.clearedAt })
            dao.prune(maxOf(now - EVENT_DAYS * DAY, meta.clearedAt))
            val events = dao.events().map { HistoryEvent(it.at, HistoryEventType.valueOf(it.type), it.app, it.activity) }
            val rows = aggregateHistory(events, cutoff.coerceAtMost(now), now, zone)
                .filterKeys { it.packageName in labels && it.packageName !in homes }
                .map { (key, usage) -> HistoryDailyEntity(key.packageName, key.date.toString(),
                    labels.getValue(key.packageName), usage.millis, usage.opens) }
            // Days the kept events fully cover are rebuilt. Older days keep the rows built while their events existed;
            // the first, partly covered day is only filled when nothing was kept for it.
            val oldest = events.minOfOrNull { it.at }
            val fullFrom = when {
                oldest == null -> today.plusDays(1)
                oldest <= cutoff -> java.time.Instant.ofEpochMilli(cutoff).atZone(zone).toLocalDate()
                else -> java.time.Instant.ofEpochMilli(oldest).atZone(zone).toLocalDate().plusDays(1)
            }.toString()
            val firstKept = today.minusDays(RETAIN_DAYS - 1L).toString()
            val kept = dao.daily().filter { it.date < fullFrom && it.date >= firstKept && it.date >= java.time.Instant.ofEpochMilli(meta.clearedAt).atZone(zone).toLocalDate().toString() }
            val keptDays = kept.map { it.date }.toSet()
            dao.clearDaily()
            dao.insertDaily(kept + rows.filter { it.date >= fullFrom || it.date !in keptDays })
            dao.saveMeta(meta.copy(collectedAt = now))
        }
    }
}
