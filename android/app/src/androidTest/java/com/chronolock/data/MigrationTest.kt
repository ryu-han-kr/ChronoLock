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
        helper.runMigrationsAndValidate(name, 5, true, ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4,
            ChronoDatabase.MIGRATION_4_5).close()

        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ChronoDatabase::class.java, name)
            .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5).build()
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
        helper.runMigrationsAndValidate(name, 5, true, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5).close()
    }

    @Test fun version3_migratesToLatest_keepingHistory() {
        helper.createDatabase(name, 3).use { db ->
            db.execSQL("INSERT INTO history_daily (packageName, date, appName, usageMillis, opens) VALUES ('com.video', '2026-10-20', 'Video', 1000, 2)")
        }
        helper.runMigrationsAndValidate(name, 5, true, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT usageMillis FROM history_daily").use { assertEquals(true, it.moveToFirst()); assertEquals(1000L, it.getLong(0)) }
        }
    }

    @Test fun version4_migratesToLatest_movingTheScheduleIntoTheList() {
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO blocking_rules (packageName, appName, enabled, scheduleStartMinute, scheduleEndMinute, scheduleDaysMask) " +
                "VALUES ('com.video', 'Video', 1, 1380, 420, 127)")
            db.execSQL("INSERT INTO blocking_rules (packageName, appName, dailyLimitSeconds, enabled) VALUES ('com.chat', 'Chat', 600, 1)")
        }
        helper.runMigrationsAndValidate(name, 5, true, ChronoDatabase.MIGRATION_4_5).close()
        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ChronoDatabase::class.java, name)
            .addMigrations(ChronoDatabase.MIGRATION_4_5).build()
        try {
            runBlocking {
                val rules = database.dao().rules().associateBy { it.packageName }
                val video = rules.getValue("com.video")
                assertEquals("1380-420-127", video.schedules)
                assertNull(video.scheduleStartMinute)
                val schedule = video.toDomain().schedules.single()
                assertEquals(java.time.LocalTime.of(23, 0), schedule.start)
                assertEquals(7, schedule.days.size)
                assertEquals(emptyList<com.chronolock.domain.Schedule>(), rules.getValue("com.chat").toDomain().schedules)
                assertNull(rules.getValue("com.chat").pausedUntilEpochMillis)
            }
        } finally { database.close() }
    }
}
