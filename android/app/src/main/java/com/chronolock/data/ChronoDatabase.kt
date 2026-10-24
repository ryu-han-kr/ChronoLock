package com.chronolock.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocking_rules")
data class RuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitSeconds: Long?,
    val maxOpenCount: Int?,
    val cooldownSeconds: Long?,
    val enabled: Boolean = true,
    val sessionLimitSeconds: Long? = null,
    val scheduleStartMinute: Int? = null,
    val scheduleEndMinute: Int? = null,
    val scheduleDaysMask: Int? = null,
    val focusUntilEpochMillis: Long? = null,
    /** All schedule windows as "startMinute-endMinute-daysMask" joined by ";"; the single-schedule columns above are legacy. */
    val schedules: String? = null,
    val pausedUntilEpochMillis: Long? = null,
    val pauseSeconds: Int? = null,
    val checkInMinutes: Int? = null,
)

/** A relaxing change held back by strict mode until [applyAtEpochMillis]; [remove] deletes the rule instead of saving [rule]. */
@Entity(tableName = "pending_rules")
data class PendingRuleEntity(
    @PrimaryKey val packageName: String,
    val applyAtEpochMillis: Long,
    val remove: Boolean,
    @Embedded(prefix = "rule_") val rule: RuleEntity,
)

/** A shared-limit group; [members] holds "packageName<TAB>appName" lines. */
@Entity(tableName = "app_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val members: String,
    val dailyLimitSeconds: Long?,
    val maxOpenCount: Int?,
    val enabled: Boolean = true,
    /** Block windows for every member, encoded like [RuleEntity.schedules]. */
    val schedules: String? = null,
)

/** A relaxing group change held back by strict mode, like [PendingRuleEntity]. */
@Entity(tableName = "pending_groups")
data class PendingGroupEntity(
    @PrimaryKey val groupId: Long,
    val applyAtEpochMillis: Long,
    val remove: Boolean,
    @Embedded(prefix = "group_") val group: GroupEntity,
)

/** A website blocked in supported browsers; see [com.chronolock.domain.BlockedSite]. */
@Entity(tableName = "blocked_sites")
data class SiteEntity(@PrimaryKey val domain: String, val enabled: Boolean = true)

/** A change that unblocks a site, held back by strict mode like [PendingRuleEntity]. */
@Entity(tableName = "pending_sites")
data class PendingSiteEntity(
    @PrimaryKey val domain: String,
    val applyAtEpochMillis: Long,
    val remove: Boolean,
    @Embedded(prefix = "site_") val site: SiteEntity,
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
    val observedPackage: String? = null,
    val blockedPackage: String? = null,
    val blockedSessionMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val wasBlocked: Boolean = false,
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
    @Query("UPDATE blocking_rules SET focusUntilEpochMillis = :until WHERE enabled = 1") suspend fun startFocus(until: Long)
    @Query("UPDATE blocking_rules SET focusUntilEpochMillis = NULL") suspend fun stopFocus()
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
    @Query("SELECT * FROM pending_rules ORDER BY applyAtEpochMillis") fun observePending(): Flow<List<PendingRuleEntity>>
    @Query("SELECT * FROM pending_rules WHERE applyAtEpochMillis <= :now") suspend fun duePending(now: Long): List<PendingRuleEntity>
    @Query("SELECT COUNT(*) FROM pending_rules") suspend fun pendingCount(): Int
    @Upsert suspend fun savePending(row: PendingRuleEntity)
    @Query("DELETE FROM pending_rules WHERE packageName = :packageName") suspend fun deletePending(packageName: String)
    @Query("DELETE FROM pending_rules") suspend fun clearPending()
    @Query("SELECT * FROM app_groups ORDER BY name") fun observeGroups(): Flow<List<GroupEntity>>
    @Query("SELECT * FROM app_groups") suspend fun groups(): List<GroupEntity>
    @Insert suspend fun insertGroup(group: GroupEntity): Long
    @Upsert suspend fun saveGroup(group: GroupEntity)
    @Query("DELETE FROM app_groups WHERE id = :id") suspend fun deleteGroup(id: Long)
    @Query("DELETE FROM app_groups") suspend fun clearGroups()
    @Query("SELECT * FROM pending_groups ORDER BY applyAtEpochMillis") fun observePendingGroups(): Flow<List<PendingGroupEntity>>
    @Query("SELECT * FROM pending_groups WHERE applyAtEpochMillis <= :now") suspend fun duePendingGroups(now: Long): List<PendingGroupEntity>
    @Upsert suspend fun savePendingGroup(row: PendingGroupEntity)
    @Query("DELETE FROM pending_groups WHERE groupId = :id") suspend fun deletePendingGroup(id: Long)
    @Query("DELETE FROM pending_groups") suspend fun clearPendingGroups()
    @Query("SELECT * FROM blocked_sites ORDER BY domain") fun observeSites(): Flow<List<SiteEntity>>
    @Query("SELECT * FROM blocked_sites") suspend fun sites(): List<SiteEntity>
    @Upsert suspend fun saveSite(site: SiteEntity)
    @Query("DELETE FROM blocked_sites WHERE domain = :domain") suspend fun deleteSite(domain: String)
    @Query("DELETE FROM blocked_sites") suspend fun clearSites()
    @Query("SELECT * FROM pending_sites ORDER BY applyAtEpochMillis") fun observePendingSites(): Flow<List<PendingSiteEntity>>
    @Query("SELECT * FROM pending_sites WHERE applyAtEpochMillis <= :now") suspend fun duePendingSites(now: Long): List<PendingSiteEntity>
    @Upsert suspend fun savePendingSite(row: PendingSiteEntity)
    @Query("DELETE FROM pending_sites WHERE domain = :domain") suspend fun deletePendingSite(domain: String)
    @Query("DELETE FROM pending_sites") suspend fun clearPendingSites()
}

@Database(entities = [RuleEntity::class, DailyEntity::class, CloseEntity::class, CheckpointEntity::class, EventEntity::class,
    HistoryEventEntity::class, HistoryDailyEntity::class, HistoryMetaEntity::class, HistoryEstimateEntity::class, PendingRuleEntity::class,
    GroupEntity::class, PendingGroupEntity::class, SiteEntity::class, PendingSiteEntity::class, HistoryYearEntity::class],
    version = 10, exportSchema = true)
abstract class ChronoDatabase : RoomDatabase() {
    abstract fun dao(): ChronoDao
    abstract fun historyDao(): HistoryDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_groups ADD COLUMN schedules TEXT")
                db.execSQL("ALTER TABLE pending_groups ADD COLUMN group_schedules TEXT")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocking_rules ADD COLUMN pauseSeconds INTEGER")
                db.execSQL("ALTER TABLE blocking_rules ADD COLUMN checkInMinutes INTEGER")
                db.execSQL("ALTER TABLE pending_rules ADD COLUMN rule_pauseSeconds INTEGER")
                db.execSQL("ALTER TABLE pending_rules ADD COLUMN rule_checkInMinutes INTEGER")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS history_year (date TEXT NOT NULL, usageMillis INTEGER NOT NULL, estimated INTEGER NOT NULL, PRIMARY KEY(date))")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS blocked_sites (domain TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(domain))")
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_sites (domain TEXT NOT NULL, applyAtEpochMillis INTEGER NOT NULL, " +
                    "remove INTEGER NOT NULL, site_domain TEXT NOT NULL, site_enabled INTEGER NOT NULL, PRIMARY KEY(domain))")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS app_groups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                    "members TEXT NOT NULL, dailyLimitSeconds INTEGER, maxOpenCount INTEGER, enabled INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_groups (groupId INTEGER NOT NULL, applyAtEpochMillis INTEGER NOT NULL, " +
                    "remove INTEGER NOT NULL, group_id INTEGER NOT NULL, group_name TEXT NOT NULL, group_members TEXT NOT NULL, " +
                    "group_dailyLimitSeconds INTEGER, group_maxOpenCount INTEGER, group_enabled INTEGER NOT NULL, PRIMARY KEY(groupId))")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocking_rules ADD COLUMN schedules TEXT")
                db.execSQL("ALTER TABLE blocking_rules ADD COLUMN pausedUntilEpochMillis INTEGER")
                // Carry the single legacy schedule into the list column.
                db.execSQL("UPDATE blocking_rules SET schedules = scheduleStartMinute || '-' || scheduleEndMinute || '-' || scheduleDaysMask, " +
                    "scheduleStartMinute = NULL, scheduleEndMinute = NULL, scheduleDaysMask = NULL " +
                    "WHERE scheduleStartMinute IS NOT NULL AND scheduleEndMinute IS NOT NULL AND scheduleDaysMask IS NOT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_rules (packageName TEXT NOT NULL, applyAtEpochMillis INTEGER NOT NULL, " +
                    "remove INTEGER NOT NULL, rule_packageName TEXT NOT NULL, rule_appName TEXT NOT NULL, rule_dailyLimitSeconds INTEGER, " +
                    "rule_maxOpenCount INTEGER, rule_cooldownSeconds INTEGER, rule_enabled INTEGER NOT NULL, rule_sessionLimitSeconds INTEGER, " +
                    "rule_scheduleStartMinute INTEGER, rule_scheduleEndMinute INTEGER, rule_scheduleDaysMask INTEGER, " +
                    "rule_focusUntilEpochMillis INTEGER, rule_schedules TEXT, rule_pausedUntilEpochMillis INTEGER, PRIMARY KEY(packageName))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS history_estimate (packageName TEXT NOT NULL, date TEXT NOT NULL, appName TEXT NOT NULL, usageMillis INTEGER NOT NULL, PRIMARY KEY(packageName, date))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS history_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, at INTEGER NOT NULL, type TEXT NOT NULL, app TEXT NOT NULL, activity TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_history_events_at_type_app_activity ON history_events (at, type, app, activity)")
                db.execSQL("CREATE TABLE IF NOT EXISTS history_daily (packageName TEXT NOT NULL, date TEXT NOT NULL, appName TEXT NOT NULL, usageMillis INTEGER NOT NULL, opens INTEGER NOT NULL, PRIMARY KEY(packageName, date))")
                db.execSQL("CREATE TABLE IF NOT EXISTS history_meta (id INTEGER NOT NULL, clearedAt INTEGER NOT NULL, collectedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("sessionLimitSeconds", "scheduleStartMinute", "scheduleEndMinute", "scheduleDaysMask", "focusUntilEpochMillis")
                    .forEach { db.execSQL("ALTER TABLE blocking_rules ADD COLUMN $it INTEGER") }
                db.execSQL("ALTER TABLE monitor_checkpoint ADD COLUMN observedPackage TEXT")
                db.execSQL("ALTER TABLE monitor_checkpoint ADD COLUMN blockedPackage TEXT")
                db.execSQL("ALTER TABLE monitor_checkpoint ADD COLUMN blockedSessionMillis INTEGER")
                db.execSQL("ALTER TABLE monitor_checkpoint ADD COLUMN wasBlocked INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
