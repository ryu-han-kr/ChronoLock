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
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4,
            ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).close()

        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ChronoDatabase::class.java, name)
            .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).build()
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
            }
        } finally { database.close() }
    }

    @Test fun version2_migratesToLatest() {
        helper.createDatabase(name, 2).close()
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).close()
    }

    @Test fun version3_migratesToLatest_keepingHistory() {
        helper.createDatabase(name, 3).use { db ->
            db.execSQL("INSERT INTO history_daily (packageName, date, appName, usageMillis, opens) VALUES ('com.video', '2026-10-20', 'Video', 1000, 2)")
        }
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT usageMillis FROM history_daily").use { assertEquals(true, it.moveToFirst()); assertEquals(1000L, it.getLong(0)) }
        }
    }

    @Test fun version4_migratesToLatest_movingTheScheduleIntoTheList() {
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO blocking_rules (packageName, appName, enabled, scheduleStartMinute, scheduleEndMinute, scheduleDaysMask) " +
                "VALUES ('com.video', 'Video', 1, 1380, 420, 127)")
            db.execSQL("INSERT INTO blocking_rules (packageName, appName, dailyLimitSeconds, enabled) VALUES ('com.chat', 'Chat', 600, 1)")
        }
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).close()
        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ChronoDatabase::class.java, name)
            .addMigrations(ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).build()
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

    @Test fun version5_migratesToLatest_keepingRulesAndAddingEmptyGroups() {
        helper.createDatabase(name, 5).use { db ->
            db.execSQL("INSERT INTO blocking_rules (packageName, appName, enabled, schedules) VALUES ('com.video', 'Video', 1, '1380-420-127')")
        }
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT schedules FROM blocking_rules").use { assertEquals(true, it.moveToFirst()); assertEquals("1380-420-127", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM app_groups").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun version6_migratesToLatest_keepingGroupsAndAddingEmptySites() {
        helper.createDatabase(name, 6).use { db ->
            db.execSQL("INSERT INTO app_groups (name, members, enabled) VALUES ('SNS', 'a\tA\nb\tB', 1)")
        }
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT name FROM app_groups").use { assertEquals(true, it.moveToFirst()); assertEquals("SNS", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM blocked_sites").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun version7_migratesToLatest_keepingSitesAndAddingAnEmptyYear() {
        helper.createDatabase(name, 7).use { db -> db.execSQL("INSERT INTO blocked_sites (domain, enabled) VALUES ('youtube.com', 1)") }
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT domain FROM blocked_sites").use { assertEquals(true, it.moveToFirst()); assertEquals("youtube.com", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM history_year").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun version8_migratesToLatest_addingEmptyPauseAndCheckIn() {
        helper.createDatabase(name, 8).use { db -> db.execSQL("INSERT INTO blocking_rules (packageName, appName, enabled) VALUES ('com.video', 'Video', 1)") }
        helper.runMigrationsAndValidate(name, 9, true, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10, ChronoDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT pauseSeconds, checkInMinutes FROM blocking_rules").use { assertEquals(true, it.moveToFirst()); assertEquals(true, it.isNull(0) && it.isNull(1)) }
        }
    }
}
