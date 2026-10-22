package com.chronolock.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.domain.AppRule
import com.chronolock.domain.ClockReading
import com.chronolock.domain.Reason
import com.chronolock.domain.Schedule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class LocalRepositoryTest {
    private lateinit var database: ChronoDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "repository-test-${UUID.randomUUID()}.db"
    private val origin = Instant.parse("2026-10-08T09:00:00Z").toEpochMilli()
    private fun clock(ms: Long, epoch: Long = origin + ms, boot: Int = 1) =
        ClockReading(epoch, ms, boot, ZoneId.of("Asia/Seoul"))

    @Before fun setup() {
        database = openDatabase()
    }
    @After fun close() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() = Room.databaseBuilder(context, ChronoDatabase::class.java, databaseName)
        .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3).build()

    @Test fun databaseReopen_restoresRuleCountersAndCooldown() = runBlocking {
        val rule = AppRule("test.app", "Test", dailyLimitSeconds = 60, maxOpenCount = 3, cooldownSeconds = 30)
        val first = LocalRepository(database)
        first.saveRule(rule)
        first.foreground("test.app", clock(0))
        first.foreground(null, clock(2000))
        database.close()

        database = openDatabase()
        assertEquals(rule, database.dao().rules().single().toDomain())
        val restored = LocalRepository(database)
        restored.reconnect(clock(3000), emptyMap())
        val blocked = restored.foreground("test.app", clock(4000))
        assertTrue(blocked.decision.denied)
        assertEquals(28L, blocked.decision.retryAfterSeconds)
        assertEquals(2000L, database.dao().daily().single().usageMillis)
        assertEquals(1, database.dao().daily().single().opens)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
        assertFalse(restored.tick(clock(32000)).decision.denied)
        assertEquals(2, database.dao().daily().single().opens)
    }

    @Test fun newRepository_restoresCountersAndDoesNotDoubleCountActiveOpen() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 60, maxOpenCount = 3))
        first.foreground("test.app", clock(0))
        first.tick(clock(1500))
        val second = LocalRepository(database)
        val restored = second.reconnect(clock(2000), mapOf("test.app" to 500L), "test.app")
        assertEquals(2000L, restored.daily.values.single().usageMillis)
        assertEquals(1, restored.daily.values.single().opens)
        second.tick(clock(3000))
        assertEquals(3000L, database.dao().daily().single().usageMillis)
    }

    @Test fun newRepository_restoresCooldownAndBlockedAttempts() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", cooldownSeconds = 30))
        first.foreground("test.app", clock(0))
        first.foreground(null, clock(1000))
        val second = LocalRepository(database)
        second.reconnect(clock(2000), emptyMap())
        val blocked = second.foreground("test.app", clock(3000))
        assertTrue(blocked.decision.denied)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
        assertFalse(second.tick(clock(31000)).decision.denied)
    }

    @Test fun clearAll_removesRulesEventsCheckpointsAndActiveState() = runBlocking {
        val repository = LocalRepository(database)
        repository.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 1))
        repository.foreground("test.app", clock(0))
        repository.tick(clock(1000))
        repository.clearAll()
        assertTrue(database.dao().rules().isEmpty())
        assertTrue(database.dao().daily().isEmpty())
        assertNull(database.dao().checkpoint())
        assertFalse(repository.tick(clock(2000)).decision.denied)
    }

    @Test fun databaseReopen_afterRebootDoesNotReuseActiveOpen() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", maxOpenCount = 1))
        first.foreground("test.app", clock(0))
        first.tick(clock(1000))
        database.close()
        database = openDatabase()

        val restored = LocalRepository(database).reconnect(clock(100, origin + 10000, 2), emptyMap(), "test.app")
        assertTrue(restored.decision.denied)
        assertNull(database.dao().checkpoint()?.activePackage)
        assertEquals(1, database.dao().daily().single().opens)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
        assertEquals(1000L, database.dao().daily().single().usageMillis)
    }

    @Test fun recoveredDailyLimit_isBlockedAndPersistedImmediately() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 2))
        first.foreground("test.app", clock(0))
        first.tick(clock(1000))

        val restored = LocalRepository(database).reconnect(clock(2000), mapOf("test.app" to 1000L), "test.app")
        assertTrue(restored.decision.denied)
        assertNull(database.dao().checkpoint()?.activePackage)
        assertEquals(2000L, database.dao().daily().single().usageMillis)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
    }

    @Test fun advancedRule_roundTripsAcrossDatabaseReopen() = runBlocking {
        val rule = AppRule("test.app", "Test", dailyLimitSeconds = 1200, maxOpenCount = 5, cooldownSeconds = 600,
            sessionLimitSeconds = 300, schedule = Schedule(LocalTime.of(23, 0), LocalTime.of(7, 0), setOf(DayOfWeek.FRIDAY)), enabled = false)
        LocalRepository(database).saveRule(rule)
        database.close()
        database = openDatabase()
        assertEquals(rule, database.dao().rules().single().toDomain())
    }

    @Test fun sessionBlock_survivesDatabaseReopen_andBillsUseAfterOverlayDied() = runBlocking {
        val repository = LocalRepository(database)
        repository.saveRule(AppRule("test.app", "Test", sessionLimitSeconds = 1))
        repository.foreground("test.app", clock(0))
        repository.tick(clock(1000))
        database.close()
        database = openDatabase()
        // The overlay died with the old service; the 9 s recovered afterwards were real use and are billed.
        val restored = LocalRepository(database).reconnect(clock(10000), mapOf("test.app" to 9000L), "test.app")
        assertEquals(listOf(Reason.SESSION_USAGE), restored.decision.reasons)
        assertEquals(10000L, database.dao().daily().single().usageMillis)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
        assertEquals(1000L, database.dao().checkpoint()?.blockedSessionMillis)
    }

    @Test fun focus_persistsDeadlineAndTargetsOnlyEnabledExistingRules() = runBlocking {
        val repository = LocalRepository(database)
        repository.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 600))
        repository.saveRule(AppRule("disabled.app", "Disabled", dailyLimitSeconds = 600, enabled = false))
        repository.startFocus(25, origin)
        repository.saveRule(AppRule("later.app", "Later", dailyLimitSeconds = 600))
        database.close()
        database = openDatabase()
        val rules = database.dao().rules().associateBy { it.packageName }
        assertEquals(origin + 25 * 60000, rules.getValue("test.app").focusUntilEpochMillis)
        assertNull(rules.getValue("disabled.app").focusUntilEpochMillis)
        assertNull(rules.getValue("later.app").focusUntilEpochMillis)
        val restored = LocalRepository(database)
        assertEquals(listOf(Reason.FOCUS), restored.reconnect(clock(1000), emptyMap(), "test.app").decision.reasons)
        assertFalse(restored.tick(clock(25 * 60000L)).decision.denied)
    }

    @Test fun focus_cancelKeepsRulesAndEditingDoesNotEraseDeadline() = runBlocking {
        val repository = LocalRepository(database)
        val rule = AppRule("test.app", "Test", sessionLimitSeconds = 300)
        repository.saveRule(rule)
        repository.startFocus(25, origin)
        repository.saveRule(rule.copy(sessionLimitSeconds = 120))
        assertEquals(origin + 25 * 60000, database.dao().rules().single().focusUntilEpochMillis)
        repository.stopFocus(origin + 1000)
        assertEquals(rule.copy(sessionLimitSeconds = 120), database.dao().rules().single().toDomain())
    }

    @Test fun focus_requiresAnEnabledTargetAndValidDuration() = runBlocking {
        val repository = LocalRepository(database)
        try { repository.startFocus(25, origin); fail("No target must be rejected") } catch (_: IllegalArgumentException) { }
        repository.saveRule(AppRule("test.app", "Test", maxOpenCount = 3))
        try { repository.startFocus(0, origin); fail("Zero duration must be rejected") } catch (_: IllegalArgumentException) { }
        assertNull(database.dao().rules().single().focusUntilEpochMillis)
    }

    @Test fun migrationFromVersionOne_preservesRulesUsageAndCheckpoint() = runBlocking {
        database.close()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.chronolock.data.ChronoDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(databaseName)
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
            old.execSQL("INSERT INTO blocking_rules VALUES ('test.app', 'Test', 60, 3, 30, 1)")
            old.execSQL("INSERT INTO daily_usage_stats VALUES ('test.app', '2026-10-08', 1500, 1, 0)")
            old.execSQL("INSERT INTO monitor_checkpoint VALUES (1, 'test.app', 1500, ?, 1500, 1, 'Asia/Seoul')", arrayOf(origin + 1500))
            old.version = 1
        }
        database = openDatabase()
        val restoredRule = database.dao().rules().single().toDomain()
        assertEquals(AppRule("test.app", "Test", 60, 3, 30), restoredRule)
        assertEquals(1500L, database.dao().daily().single().usageMillis)
        assertEquals(1500L, database.dao().checkpoint()?.sessionMillis)
        assertFalse(database.dao().checkpoint()!!.wasBlocked)
        LocalRepository(database).saveRule(restoredRule.copy(sessionLimitSeconds = 5))
        assertEquals(5L, database.dao().rules().single().sessionLimitSeconds)
    }
}
