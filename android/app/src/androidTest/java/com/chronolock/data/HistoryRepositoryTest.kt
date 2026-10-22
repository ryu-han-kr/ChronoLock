package com.chronolock.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.blocking.AndroidEnvironment
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class HistoryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "history-test-${UUID.randomUUID()}.db"
    private var db = Room.databaseBuilder(context, ChronoDatabase::class.java, name)
        .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4).build()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-10-15T03:00:00Z").toEpochMilli()
    private val labels = mapOf("app.a" to "App A", "home" to "Home")
    private fun event(at: Long, type: String, app: String = "app.a") = HistoryEventEntity(at = at, type = type, app = app, activity = "Main")
    private fun repository() = HistoryRepository(context, db, AndroidEnvironment(context))
    @After fun close() { db.close(); context.deleteDatabase(name) }

    private val day = 86_400_000L
    private fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    private fun bucket(app: String, from: Long, to: Long, millis: Long) = com.chronolock.domain.UsageBucket(app, from, to, millis)

    @Test fun estimates_fillOnlyDaysBeforeFirstRecord_andLeaveHomeOut() = runBlocking {
        repository().importEvents(listOf(event(now - 2000, "RESUME"), event(now - 1000, "PAUSE")), labels, setOf("home"), now, zone)
        val weekly = listOf(bucket("app.a", now - 6 * day, now, 7_000L), bucket("home", now - 6 * day, now, 9_000L))
        repository().importEstimates(listOf(emptyList(), weekly), labels, setOf("home"), now, zone)
        val rows = db.historyDao().estimates()
        assertEquals(6, rows.size) // six days before today; today has a real record
        assertTrue(rows.all { it.packageName == "app.a" && it.date < date(now).toString() })
        assertEquals(1000L, rows.first().usageMillis) // (7 s - 1 s recorded) / 6 unknown days
    }

    @Test fun estimates_keepDaysAndroidNoLongerReports_butDropRecordedAndExpiredDays() = runBlocking {
        val first = listOf(listOf(bucket("app.a", now - 3 * day, now - 3 * day + 1000, 5_000L)))
        repository().importEstimates(first, labels, emptySet(), now, zone)
        assertEquals(listOf(date(now - 3 * day).toString()), db.historyDao().estimates().map { it.date })
        // Next day: Android dropped that bucket; the estimate stays.
        repository().importEstimates(listOf(emptyList()), labels, emptySet(), now + day, zone)
        assertEquals(1, db.historyDao().estimates().size)
        // A real record on that day replaces the estimate.
        val recordAt = now - 3 * day
        repository().importEvents(listOf(event(recordAt, "RESUME"), event(recordAt + 1000, "PAUSE")), labels, emptySet(), now + day, zone)
        repository().importEstimates(listOf(emptyList()), labels, emptySet(), now + day, zone)
        assertTrue(db.historyDao().estimates().isEmpty())
    }

    @Test fun clearAll_removesEstimates_andNoEstimateBeforeTheReset() = runBlocking {
        repository().importEstimates(listOf(listOf(bucket("app.a", now - 3 * day, now - 3 * day + 1000, 5_000L))), labels, emptySet(), now, zone)
        LocalRepository(db).clearAll()
        assertTrue(db.historyDao().estimates().isEmpty())
        val cleared = db.historyDao().meta()!!.clearedAt
        val beforeReset = listOf(listOf(bucket("app.a", cleared - 2 * day, cleared - 2 * day + 1000, 5_000L)))
        repository().importEstimates(beforeReset, labels, emptySet(), cleared + 3 * day, zone)
        assertTrue(db.historyDao().estimates().isEmpty())
    }

    @Test fun repeatImport_isIdempotent_withoutRules_andPersistsAfterReopen() = runBlocking {
        val events = listOf(event(now - 5000, "RESUME"), event(now - 1000, "PAUSE"))
        repository().importEvents(events, labels, setOf("home"), now, zone)
        repository().importEvents(events, labels, setOf("home"), now, zone)
        assertTrue(db.dao().rules().isEmpty())
        assertEquals(2, db.historyDao().events().size)
        assertEquals(4000L, db.historyDao().daily().single().usageMillis)
        assertEquals(1, db.historyDao().daily().single().opens)
        db.close()
        db = Room.databaseBuilder(context, ChronoDatabase::class.java, name).build()
        assertEquals(4000L, db.historyDao().daily().single().usageMillis)
    }
    @Test fun laterImport_retainsDaysNoLongerAvailableFromAndroid() = runBlocking {
        val older = now - 20 * 86_400_000L
        repository().importEvents(listOf(event(older, "RESUME"), event(older + 1000, "PAUSE")), labels, emptySet(), now - 19 * 86_400_000L, zone)
        repository().importEvents(listOf(event(now - 2000, "RESUME"), event(now - 1000, "PAUSE")), labels, emptySet(), now, zone)
        assertEquals(2, db.historyDao().daily().size)
        assertEquals(2000L, db.historyDao().daily().sumOf { it.usageMillis })
    }
    @Test fun clearAll_preventsOldEventsFromBeingImportedAgain() = runBlocking {
        val before = System.currentTimeMillis() - 10_000
        val events = listOf(event(before, "RESUME"), event(before + 1000, "PAUSE"))
        repository().importEvents(events, labels, emptySet(), before + 2000, zone)
        LocalRepository(db).clearAll()
        assertTrue(db.historyDao().events().isEmpty())
        assertTrue(db.historyDao().daily().isEmpty())
        val cutoff = db.historyDao().meta()!!.clearedAt
        repository().importEvents(events + listOf(event(cutoff + 100, "RESUME"), event(cutoff + 1100, "PAUSE")),
            labels, emptySet(), cutoff + 2000, zone)
        assertEquals(HistoryDailyEntity("app.a", Instant.ofEpochMilli(cutoff).atZone(zone).toLocalDate().toString(), "App A", 1000, 1), db.historyDao().daily().single())
        assertEquals(2, db.historyDao().events().size)
    }
    @Test fun retentionAndHomeExclusion_doNotRemoveOtherApps() = runBlocking {
        val old = now - 32 * 86_400_000L
        val events = listOf(event(old, "RESUME"), event(old + 1000, "PAUSE"), event(now - 6000, "RESUME"),
            event(now - 4000, "RESUME", "home"), event(now - 2000, "RESUME"), event(now - 1000, "PAUSE"))
        repository().importEvents(events, labels, setOf("home"), now, zone)
        assertEquals(3000L, db.historyDao().daily().single().usageMillis)
        assertEquals(2, db.historyDao().daily().single().opens)
        assertTrue(db.historyDao().events().all { it.at > old + 1000 })
    }
    @Test fun versionTwoMigration_preservesExistingRulesAndCounters() = runBlocking {
        db.close()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.chronolock.data.ChronoDatabase/2.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO blocking_rules (packageName, appName, dailyLimitSeconds, enabled) VALUES ('app.a', 'App A', 3600, 1)")
            old.execSQL("INSERT INTO daily_usage_stats VALUES ('app.a', '2026-10-15', 12345, 3, 1)")
            old.version = 2
        }
        db = Room.databaseBuilder(context, ChronoDatabase::class.java, name).addMigrations(ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4).build()
        assertEquals(3600L, db.dao().rules().single().dailyLimitSeconds)
        assertEquals(12345L, db.dao().daily().single().usageMillis)
        assertTrue(db.historyDao().daily().isEmpty())
        repository().importEvents(listOf(event(now - 5000, "RESUME"), event(now - 1000, "PAUSE")), labels, emptySet(), now, zone)
        assertEquals(4000L, db.historyDao().daily().single().usageMillis)
    }
}
