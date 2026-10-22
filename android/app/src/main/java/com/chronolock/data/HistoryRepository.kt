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

    /** Import overlapping OS events idempotently, retaining a local rolling 30-day history. */
    @Suppress("DEPRECATION")
    suspend fun collect() = mutex.withLock { withContext(Dispatchers.IO) {
        if (!environment.hasUsageAccess()) return@withContext
        check(context.getSystemService(UserManager::class.java).isUserUnlocked)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val previous = dao.meta() ?: HistoryMetaEntity()
        // Keep at most 30 elapsed days; this also gives context before the first displayed midnight.
        val queryFrom = maxOf(now - 30 * 86_400_000L, previous.clearedAt,
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
        // Events only reach back about a week; usage-stats buckets (finest first) estimate the older days of the window.
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val windowStart = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        val buckets = listOf(UsageStatsManager.INTERVAL_DAILY, UsageStatsManager.INTERVAL_WEEKLY, UsageStatsManager.INTERVAL_MONTHLY).map { interval ->
            usage.queryUsageStats(interval, windowStart, now).orEmpty().filter { it.totalTimeInForeground > 0 }
                .map { UsageBucket(it.packageName, it.firstTimeStamp, it.lastTimeStamp, it.totalTimeInForeground) }
        }
        importEstimates(buckets, names, homes, now, zone)
    } }

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
            val windowStart = today.minusDays(29)
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
        val from = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(29)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        database.withTransaction {
            // Read again inside the transaction: deletion must never re-import pre-deletion records.
            val meta = dao.meta() ?: HistoryMetaEntity()
            val cutoff = maxOf(from, meta.clearedAt)
            val labels = dao.daily().associate { it.packageName to it.appName } + names
            dao.insertEvents(collected.filter { it.at >= meta.clearedAt })
            dao.prune(maxOf(now - 30 * 86_400_000L, meta.clearedAt))
            val events = dao.events().map { HistoryEvent(it.at, HistoryEventType.valueOf(it.type), it.app, it.activity) }
            val rows = aggregateHistory(events, cutoff.coerceAtMost(now), now, zone)
                .filterKeys { it.packageName in labels && it.packageName !in homes }
                .map { (key, usage) -> HistoryDailyEntity(key.packageName, key.date.toString(),
                    labels.getValue(key.packageName), usage.millis, usage.opens) }
            dao.clearDaily()
            dao.insertDaily(rows)
            dao.saveMeta(meta.copy(collectedAt = now))
        }
    }
}
