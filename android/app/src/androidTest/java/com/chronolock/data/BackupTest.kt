package com.chronolock.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronolock.domain.AppGroup
import com.chronolock.domain.AppRule
import com.chronolock.domain.BlockedSite
import com.chronolock.domain.Schedule
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.first
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

    private val sites = listOf(BlockedSite("youtube.com"), BlockedSite("x.com", enabled = false))

    @Test fun lookAndFeel_roundTrips_andOlderFilesLeaveItAlone() {
        val backup = Backup(emptyList(), emptyList(), "LIGHT", nightTheme = "OCEAN", followSystem = true,
            favoriteThemes = setOf("FOREST", "LAVENDER"), mascot = "OTTER", reduceMotion = true)
        val decoded = BackupCodec.decode(BackupCodec.encode(backup, 0))
        assertEquals("OCEAN", decoded.nightTheme)
        assertEquals(true, decoded.followSystem)
        assertEquals(setOf("FOREST", "LAVENDER"), decoded.favoriteThemes)
        assertEquals("OTTER", decoded.mascot)
        assertEquals(true, decoded.reduceMotion)
        val older = JSONObject(BackupCodec.encode(Backup(emptyList(), emptyList(), "LIGHT"), 0)).put("version", 2)
        val restored = BackupCodec.decode(older.toString())
        assertEquals(null, restored.mascot)
        assertEquals(null, restored.favoriteThemes)
    }

    @Test fun roundTrip_keepsLimitsSchedulesGroupsAndSettings_butNotFocusOrPause() {
        val text = BackupCodec.encode(Backup(listOf(rule), listOf(group), "LIGHT", "ENGLISH", 30, sites), 1234)
        val decoded = BackupCodec.decode(text)
        assertEquals(listOf(rule.copy(focusUntilEpochMillis = null, pausedUntilEpochMillis = null)), decoded.rules)
        assertEquals(listOf(group.copy(id = 0)), decoded.groups)
        assertEquals(Triple("LIGHT", "ENGLISH", 30), Triple(decoded.theme, decoded.language, decoded.strictMinutes))
        assertEquals(sites, decoded.sites)
    }

    @Test fun versionOneFiles_restoreWithoutSites() {
        val old = JSONObject(BackupCodec.encode(Backup(listOf(rule), listOf(group)), 0)).put("version", 1).apply { remove("sites") }
        assertEquals(emptyList<BlockedSite>(), BackupCodec.decode(old.toString()).sites)
    }

    @Test fun foreignOrBrokenFiles_areRejected() {
        val valid = JSONObject(BackupCodec.encode(Backup(listOf(rule), listOf(group)), 0))
        listOf("", "not json", "{}", JSONObject(valid.toString()).put("version", 99).toString(),
            JSONObject(valid.toString()).apply { getJSONArray("rules").getJSONObject(0).put("dailyLimitSeconds", -5) }.toString(),
            JSONObject(valid.toString()).put("sites", org.json.JSONArray().put(JSONObject().put("domain", "not a site"))).toString(),
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
            repository.requestSite(BlockedSite("old.com"), 0, 0)
            repository.replaceAll(listOf(rule), listOf(group), sites, 0)
            assertEquals(listOf("com.video"), database.dao().rules().map { it.packageName })
            assertNull(database.dao().rules().single().focusUntilEpochMillis)
            assertEquals(listOf("SNS"), database.dao().groups().map { it.name })
            assertEquals(listOf("youtube.com"), repository.blockedSites(0).map { it.domain })
            assertEquals(1, database.dao().daily().size)
        } finally { database.close() }
    }

    @Test fun weeklyBackup_withoutAFolder_goesToTheDownloadFolderAndReplacesItsFile() = runBlocking {
        org.junit.Assume.assumeTrue(BackupRepository.PHONE_BACKUP_SUPPORTED)
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, ChronoDatabase::class.java).build()
        val preferences = Preferences(context)
        var file: android.net.Uri? = null
        try {
            val repository = LocalRepository(database)
            repository.saveRule(rule)
            preferences.setAutoBackupFolder(null)
            val backups = BackupRepository(context, repository, preferences)
            assertTrue(backups.autoBackup(1_000))
            file = android.net.Uri.parse(requireNotNull(preferences.autoBackup.first().phoneFile))
            // Not due again within the week; forced, it rewrites the same file instead of adding another.
            assertFalse(backups.autoBackup(2_000))
            assertTrue(backups.autoBackup(3_000, force = true))
            assertEquals(file.toString(), preferences.autoBackup.first().phoneFile)
            val text = context.contentResolver.openInputStream(file)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals(3_000L, JSONObject(text).getLong("exportedAt"))
            assertEquals(listOf("com.video"), BackupCodec.decode(text).rules.map { it.packageName })
        } finally {
            file?.let { context.contentResolver.delete(it, null, null) }
            preferences.setAutoBackupFolder(null)
            database.close()
        }
    }
}
