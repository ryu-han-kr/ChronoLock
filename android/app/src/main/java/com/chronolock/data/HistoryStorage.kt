package com.chronolock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history_events", indices = [Index(value = ["at", "type", "app", "activity"], unique = true)])
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long, val type: String, val app: String, val activity: String,
)

@Entity(tableName = "history_daily", primaryKeys = ["packageName", "date"])
data class HistoryDailyEntity(val packageName: String, val date: String, val appName: String, val usageMillis: Long, val opens: Int)

/** Usage estimated from OS usage-stats buckets for a day that has no event records (before the event window). */
@Entity(tableName = "history_estimate", primaryKeys = ["packageName", "date"])
data class HistoryEstimateEntity(val packageName: String, val date: String, val appName: String, val usageMillis: Long)

/** One day's total use for the year calendar; kept a year, long after the per-app rows are gone. */
@Entity(tableName = "history_year")
data class HistoryYearEntity(@PrimaryKey val date: String, val usageMillis: Long, val estimated: Boolean)

@Entity(tableName = "history_meta")
data class HistoryMetaEntity(@PrimaryKey val id: Int = 1, val clearedAt: Long = 0, val collectedAt: Long = 0)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_daily ORDER BY date DESC") fun observeDaily(): Flow<List<HistoryDailyEntity>>
    @Query("SELECT * FROM history_meta WHERE id = 1") fun observeMeta(): Flow<HistoryMetaEntity?>
    @Query("SELECT * FROM history_meta WHERE id = 1") suspend fun meta(): HistoryMetaEntity?
    @Query("SELECT * FROM history_daily") suspend fun daily(): List<HistoryDailyEntity>
    @Query("SELECT * FROM history_events ORDER BY at, id") suspend fun events(): List<HistoryEventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvents(events: List<HistoryEventEntity>)
    @Insert suspend fun insertDaily(rows: List<HistoryDailyEntity>)
    @Upsert suspend fun saveMeta(meta: HistoryMetaEntity)
    @Query("DELETE FROM history_events WHERE at < :before") suspend fun prune(before: Long)
    @Query("DELETE FROM history_daily") suspend fun clearDaily()
    @Query("DELETE FROM history_events") suspend fun clearEvents()
    @Query("SELECT * FROM history_estimate ORDER BY date DESC") fun observeEstimates(): Flow<List<HistoryEstimateEntity>>
    @Query("SELECT * FROM history_estimate") suspend fun estimates(): List<HistoryEstimateEntity>
    @Upsert suspend fun saveEstimates(rows: List<HistoryEstimateEntity>)
    /** Days that now have real records, or that fell out of the kept window, lose their estimate. */
    @Query("DELETE FROM history_estimate WHERE date >= :from OR date < :before") suspend fun dropEstimates(from: String, before: String)
    @Query("DELETE FROM history_estimate") suspend fun clearEstimates()
    @Query("SELECT * FROM history_year ORDER BY date") fun observeYear(): Flow<List<HistoryYearEntity>>
    @Query("SELECT * FROM history_year") suspend fun year(): List<HistoryYearEntity>
    @Upsert suspend fun saveYear(rows: List<HistoryYearEntity>)
    @Query("DELETE FROM history_year WHERE date < :before") suspend fun pruneYear(before: String)
    @Query("DELETE FROM history_year") suspend fun clearYear()
}
