package com.chronolock.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronolock.domain.AppGroup
import com.chronolock.domain.AppRule
import com.chronolock.domain.Schedule
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Backups survive a round trip, reject foreign or broken files, and a restore replaces rules and groups only. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val sleep = Schedule(LocalTime.of(23, 0), LocalTime.of(7, 0), DayOfWeek.values().toSet())
    private val rule = AppRule("com.video", "Video", dailyLimitSeconds = 1800, maxOpenCount = 5, cooldownSeconds = 60, sessionLimitSeconds = 600,
        schedules = listOf(sleep), focusUntilEpochMillis = 99, enabled = false, pausedUntilEpochMillis = 77)
    private val group = AppGroup(4, "SNS", mapOf("com.a" to "A", "com.b" to "B"), dailyLimitSeconds = 3600, maxOpenCount = 9)

    @Test fun roundTrip_keepsLimitsSchedulesGroupsAndSettings_butNotFocusOrPause() {
        val text = BackupCodec.encode(Backup(listOf(rule), listOf(group), "LIGHT", "ENGLISH", 30), 1234)
        val decoded = BackupCodec.decode(text)
        assertEquals(listOf(rule.copy(focusUntilEpochMillis = null, pausedUntilEpochMillis = null)), decoded.rules)
        assertEquals(listOf(group.copy(id = 0)), decoded.groups)
        assertEquals(Triple("LIGHT", "ENGLISH", 30), Triple(decoded.theme, decoded.language, decoded.strictMinutes))
    }

    @Test fun foreignOrBrokenFiles_areRejected() {
        val valid = JSONObject(BackupCodec.encode(Backup(listOf(rule), listOf(group)), 0))
        listOf("", "not json", "{}", JSONObject(valid.toString()).put("version", 99).toString(),
            JSONObject(valid.toString()).apply { getJSONArray("rules").getJSONObject(0).put("dailyLimitSeconds", -5) }.toString(),
            JSONObject(valid.toString()).apply { getJSONArray("groups").put(getJSONArray("groups").getJSONObject(0)) }.toString(),
        ).forEach { text ->
            assertEquals(text, BackupCodec.INVALID, runCatching { BackupCodec.decode(text) }.exceptionOrNull()?.message)
        }
    }

    @Test fun replaceAll_swapsRulesAndGroupsAndKeepsUsage() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, ChronoDatabase::class.java).build()
        try {
            val repository = LocalRepository(database)
            repository.saveRule(AppRule("com.old", "Old", maxOpenCount = 1))
            repository.requestGroup(AppGroup(0, "Old", mapOf("com.x" to "X", "com.y" to "Y"), maxOpenCount = 1), 0, 0)
            database.dao().saveDaily(listOf(DailyEntity("com.old", "2026-10-22", 1000, 1, 0)))
            repository.replaceAll(listOf(rule), listOf(group), 0)
            assertEquals(listOf("com.video"), database.dao().rules().map { it.packageName })
            assertNull(database.dao().rules().single().focusUntilEpochMillis)
            assertEquals(listOf("SNS"), database.dao().groups().map { it.name })
            assertEquals(1, database.dao().daily().size)
        } finally { database.close() }
    }
}
