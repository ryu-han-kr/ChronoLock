package com.chronolock.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.domain.AppGroup
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
        .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8).build()

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

    @Test fun resetRules_removesRulesGroupsAndSites_butKeepsTodaysUsage() = runBlocking {
        val repository = LocalRepository(database)
        repository.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 1))
        repository.requestGroup(AppGroup(0, "SNS", mapOf("a" to "A", "b" to "B"), maxOpenCount = 3), 0, origin)
        repository.requestSite(com.chronolock.domain.BlockedSite("youtube.com"), 0, origin)
        repository.foreground("test.app", clock(0))
        repository.tick(clock(1000))
        repository.resetRules(origin)
        assertTrue(database.dao().rules().isEmpty())
        assertTrue(database.dao().groups().isEmpty())
        assertTrue(database.dao().sites().isEmpty())
        // Usage counted today stays, so a reset cannot be used to clear what was used.
        assertTrue(database.dao().daily().isNotEmpty())
        assertFalse(repository.tick(clock(2000)).decision.denied)
    }

    @Test fun lock_refusesEveryLoosening_butLetsTighteningThrough() = runBlocking {
        val repository = LocalRepository(database)
        val locked = LocalRepository.LOCKED_DELAY
        repository.saveRule(AppRule("a", "A", dailyLimitSeconds = 600))
        repository.requestSite(com.chronolock.domain.BlockedSite("youtube.com"), 0, origin)
        repository.requestGroup(AppGroup(0, "SNS", mapOf("a" to "A", "b" to "B"), maxOpenCount = 3), 0, origin)
        val group = database.dao().groups().single().toDomain()
        fun refused(block: suspend () -> Any?) = runBlocking { assertEquals(LocalRepository.LOCKED, runCatching { block() }.exceptionOrNull()?.message) }
        refused { repository.requestDelete("a", locked, origin) }
        refused { repository.requestRule(AppRule("a", "A", dailyLimitSeconds = 1200), locked, origin) }
        refused { repository.requestPause("a", 10, locked, origin, java.time.ZoneId.of("Asia/Seoul")) }
        refused { repository.requestSite(com.chronolock.domain.BlockedSite("youtube.com", enabled = false), locked, origin) }
        refused { repository.requestDeleteGroup(group.id, locked, origin) }
        // Tightening goes through at once.
        assertNull(repository.requestRule(AppRule("a", "A", dailyLimitSeconds = 300), locked, origin))
        assertEquals(300L, database.dao().rules().single().dailyLimitSeconds)
        assertNull(repository.requestSite(com.chronolock.domain.BlockedSite("x.com"), locked, origin))
        assertEquals(2, database.dao().sites().size)
        assertEquals(0, database.dao().pendingCount())
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
            sessionLimitSeconds = 300, schedules = listOf(Schedule(LocalTime.of(23, 0), LocalTime.of(7, 0), setOf(DayOfWeek.FRIDAY)),
                Schedule(LocalTime.of(9, 30), LocalTime.of(18, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))), enabled = false)
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

    @Test fun strictMode_holdsRelaxingChangesUntilTheDelayThenAppliesThemOnTick() = runBlocking {
        val repository = LocalRepository(database)
        val rule = AppRule("test.app", "Test", maxOpenCount = 1)
        repository.saveRule(rule)
        val now = origin
        // Tightening applies at once even in strict mode.
        assertNull(repository.requestRule(rule.copy(maxOpenCount = 1, dailyLimitSeconds = 60), 600_000, now))
        assertEquals(60L, database.dao().rules().single().dailyLimitSeconds)
        // Turning the rule off waits.
        assertEquals(now + 600_000, repository.requestRule(rule.copy(enabled = false), 600_000, now))
        assertTrue(database.dao().rules().single().enabled)
        repository.foreground("test.app", clock(0, now + 1000))
        assertTrue(database.dao().rules().single().enabled)
        repository.tick(clock(600_000, now + 600_000))
        assertFalse(database.dao().rules().single().enabled)
        assertTrue(database.dao().duePending(Long.MAX_VALUE).isEmpty())
    }

    @Test fun strictMode_deleteWaits_andCancelKeepsTheRule() = runBlocking {
        val repository = LocalRepository(database)
        repository.saveRule(AppRule("test.app", "Test", maxOpenCount = 1))
        assertNotNull(repository.requestDelete("test.app", 600_000, origin))
        repository.cancelPending("test.app")
        repository.applyDuePending(origin + 700_000)
        assertEquals(1, database.dao().rules().size)
        assertNotNull(repository.requestDelete("test.app", 600_000, origin))
        repository.applyDuePending(origin + 600_000)
        assertTrue(database.dao().rules().isEmpty())
    }

    @Test fun pause_allowsTheAppUntilItEnds_andEditsKeepThePause() = runBlocking {
        val repository = LocalRepository(database)
        val zone = ZoneId.of("Asia/Seoul")
        repository.saveRule(AppRule("test.app", "Test", maxOpenCount = 1))
        repository.foreground("test.app", clock(0))
        repository.foreground(null, clock(1000))
        assertTrue(repository.foreground("test.app", clock(2000)).decision.denied)
        assertNull(repository.requestPause("test.app", 10, 0, origin + 2500, zone))
        repository.foreground(null, clock(2600))
        assertFalse(repository.foreground("test.app", clock(3000)).decision.denied)
        // Saving from the editor keeps the running pause.
        repository.saveRule(AppRule("test.app", "Test", maxOpenCount = 2))
        assertEquals(origin + 2500 + 600_000, database.dao().rules().single().pausedUntilEpochMillis)
        repository.resume("test.app", origin + 4000)
        assertNull(database.dao().rules().single().pausedUntilEpochMillis)
    }

    @Test fun strictPause_startsAfterTheDelay() = runBlocking {
        val repository = LocalRepository(database)
        repository.saveRule(AppRule("test.app", "Test", maxOpenCount = 1))
        val applyAt = repository.requestPause("test.app", null, 600_000, origin, ZoneId.of("Asia/Seoul"))
        assertEquals(origin + 600_000, applyAt)
        assertNull(database.dao().rules().single().pausedUntilEpochMillis)
        repository.applyDuePending(origin + 600_000)
        // Until midnight Seoul time after the delay: 2026-10-08 18:10 KST -> 2026-10-09 00:00 KST.
        assertEquals(Instant.parse("2026-10-08T15:00:00Z").toEpochMilli(), database.dao().rules().single().pausedUntilEpochMillis)
    }

    @Test fun group_sharesItsLimitAcrossMembersAndSurvivesReopen() = runBlocking {
        val repository = LocalRepository(database)
        // "b" has no rule of its own; the group alone makes the monitor track and limit it.
        repository.saveRule(AppRule("a", "A", sessionLimitSeconds = 3600))
        assertNull(repository.requestGroup(AppGroup(0, "SNS", mapOf("a" to "A", "b" to "B"), dailyLimitSeconds = 60), 0, origin))
        val group = database.dao().groups().single().toDomain()
        assertEquals(mapOf("a" to "A", "b" to "B"), group.members)
        repository.foreground("a", clock(0))
        repository.foreground("b", clock(40_000))
        assertFalse(repository.tick(clock(59_000)).decision.denied)
        assertEquals(listOf(Reason.GROUP_USAGE), repository.tick(clock(60_000)).decision.reasons)
        database.close()
        database = openDatabase()
        val reopened = LocalRepository(database)
        assertTrue(reopened.foreground("a", clock(61_000)).decision.denied)
        assertEquals(listOf("a"), database.dao().rules().map { it.packageName })
    }

    @Test fun group_appsMayJoinSeveralGroupsAndStrictModeHoldsRelaxingEdits() = runBlocking {
        val repository = LocalRepository(database)
        repository.requestGroup(AppGroup(0, "SNS", mapOf("a" to "A", "b" to "B"), maxOpenCount = 3), 0, origin)
        val id = database.dao().groups().single().id
        repository.requestGroup(AppGroup(0, "Video", mapOf("b" to "B", "c" to "C"), maxOpenCount = 3), 0, origin)
        assertEquals(2, database.dao().groups().size)
        repository.requestDeleteGroup(database.dao().groups().first { it.name == "Video" }.id, 0, origin)
        val saved = database.dao().groups().single().toDomain()
        // Adding a member tightens and applies at once; raising the limit waits.
        assertNull(repository.requestGroup(saved.copy(members = saved.members + ("c" to "C")), 600_000, origin))
        assertEquals(3, database.dao().groups().single().toDomain().members.size)
        assertEquals(origin + 600_000, repository.requestGroup(saved.copy(maxOpenCount = 9), 600_000, origin))
        assertEquals(3, database.dao().groups().single().maxOpenCount)
        repository.cancelPendingGroup(id)
        repository.applyDuePending(origin + 700_000)
        assertEquals(3, database.dao().groups().single().maxOpenCount)
        assertNotNull(repository.requestDeleteGroup(id, 600_000, origin))
        repository.tick(clock(600_000, origin + 600_000))
        assertTrue(database.dao().groups().isEmpty())
    }
}
