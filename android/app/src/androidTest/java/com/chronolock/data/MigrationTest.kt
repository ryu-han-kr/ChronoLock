package com.chronolock.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each schema step keeps user data, and the migrated schema matches the exported one Room expects. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ChronoDatabase::class.java)

    @Test fun version1_migratesToLatest_keepingRulesAndCheckpoint() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO blocking_rules (packageName, appName, dailyLimitSeconds, maxOpenCount, cooldownSeconds, enabled) VALUES ('com.video', 'Video', 1800, 5, NULL, 1)")
            db.execSQL("INSERT INTO monitor_checkpoint (id, activePackage, sessionMillis, epochMillis, elapsedMillis, bootId, zone) VALUES (1, 'com.video', 1000, 2000, 3000, 4, 'Asia/Seoul')")
        }
        // Validates every table, column and index against schemas/3.json.
        helper.runMigrationsAndValidate(name, 4, true, ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4).close()

        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ChronoDatabase::class.java, name)
            .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4).build()
        try {
            runBlocking {
                val rule = database.dao().rules().single()
                assertEquals("com.video", rule.packageName)
                assertEquals(1800L, rule.dailyLimitSeconds)
                assertEquals(5, rule.maxOpenCount)
                assertNull(rule.sessionLimitSeconds)
                val checkpoint = database.dao().checkpoint()!!
                assertEquals("com.video", checkpoint.activePackage)
                assertFalse(checkpoint.wasBlocked)
                assertEquals(emptyList<HistoryDailyEntity>(), database.historyDao().daily())
                assertEquals(emptyList<HistoryEstimateEntity>(), database.historyDao().estimates())
            }
        } finally { database.close() }
    }

    @Test fun version2_migratesToLatest() {
        helper.createDatabase(name, 2).close()
        helper.runMigrationsAndValidate(name, 4, true, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4).close()
    }

    @Test fun version3_migratesToLatest_keepingHistory() {
        helper.createDatabase(name, 3).use { db ->
            db.execSQL("INSERT INTO history_daily (packageName, date, appName, usageMillis, opens) VALUES ('com.video', '2026-10-20', 'Video', 1000, 2)")
        }
        helper.runMigrationsAndValidate(name, 4, true, ChronoDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT usageMillis FROM history_daily").use { assertEquals(true, it.moveToFirst()); assertEquals(1000L, it.getLong(0)) }
        }
    }
}
