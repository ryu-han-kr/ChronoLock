package com.chronolock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocking_rules")
data class RuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitSeconds: Long?,
    val maxOpenCount: Int?,
    val cooldownSeconds: Long?,
    val enabled: Boolean = true,
)

@Entity(tableName = "daily_usage_stats", primaryKeys = ["packageName", "date"])
data class DailyEntity(val packageName: String, val date: String, val usageMillis: Long, val opens: Int, val blockedAttempts: Int)

@Entity(tableName = "last_closed")
data class CloseEntity(@PrimaryKey val packageName: String, val epochMillis: Long, val elapsedMillis: Long, val bootId: Int)

@Entity(tableName = "monitor_checkpoint")
data class CheckpointEntity(
    @PrimaryKey val id: Int = 1,
    val activePackage: String?,
    val sessionMillis: Long,
    val epochMillis: Long,
    val elapsedMillis: Long,
    val bootId: Int,
    val zone: String,
)

@Entity(tableName = "app_usage_events", indices = [Index("epochMillis")])
data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val type: String, val packageName: String?, val epochMillis: Long)

@Dao
interface ChronoDao {
    @Query("SELECT * FROM blocking_rules ORDER BY appName") fun observeRules(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM daily_usage_stats ORDER BY date DESC") fun observeDaily(): Flow<List<DailyEntity>>
    @Query("SELECT * FROM blocking_rules") suspend fun rules(): List<RuleEntity>
    @Query("SELECT * FROM daily_usage_stats") suspend fun daily(): List<DailyEntity>
    @Query("SELECT * FROM last_closed") suspend fun closes(): List<CloseEntity>
    @Query("SELECT * FROM monitor_checkpoint WHERE id = 1") suspend fun checkpoint(): CheckpointEntity?
    @Upsert suspend fun saveRule(rule: RuleEntity)
    @Query("DELETE FROM blocking_rules WHERE packageName = :packageName") suspend fun deleteRule(packageName: String)
    @Upsert suspend fun saveDaily(rows: List<DailyEntity>)
    @Upsert suspend fun saveCloses(rows: List<CloseEntity>)
    @Upsert suspend fun saveCheckpoint(row: CheckpointEntity)
    @Insert suspend fun insertEvents(rows: List<EventEntity>)
    @Query("DELETE FROM app_usage_events WHERE epochMillis < :before") suspend fun pruneEvents(before: Long)
    @Query("DELETE FROM daily_usage_stats WHERE date < :before") suspend fun pruneDaily(before: String)
    @Query("DELETE FROM blocking_rules") suspend fun clearRules()
    @Query("DELETE FROM daily_usage_stats") suspend fun clearDaily()
    @Query("DELETE FROM last_closed") suspend fun clearCloses()
    @Query("DELETE FROM monitor_checkpoint") suspend fun clearCheckpoint()
    @Query("DELETE FROM app_usage_events") suspend fun clearEvents()
}

@Database(entities = [RuleEntity::class, DailyEntity::class, CloseEntity::class, CheckpointEntity::class, EventEntity::class], version = 1, exportSchema = true)
abstract class ChronoDatabase : RoomDatabase() { abstract fun dao(): ChronoDao }
