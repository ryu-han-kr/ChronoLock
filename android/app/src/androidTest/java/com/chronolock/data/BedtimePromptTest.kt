package com.chronolock.data

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.domain.AppRule
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Plugging in at night asks once, only when turned on; the answer starts a focus that runs until morning. */
@RunWith(AndroidJUnit4::class)
class BedtimePromptTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications get() = context.getSystemService(NotificationManager::class.java).activeNotifications
        .filter { it.notification.channelId == "bedtime" }

    /** Posting and cancelling reach the notification list a moment later. */
    private fun settled(count: Int): Boolean {
        val until = android.os.SystemClock.elapsedRealtime() + 3_000
        while (android.os.SystemClock.elapsedRealtime() < until) {
            if (notifications.size == count) { android.os.SystemClock.sleep(300); if (notifications.size == count) return true }
            android.os.SystemClock.sleep(100)
        }
        return false
    }

    @Test fun chargingAtNight_asksOncePerNight_andOnlyWhenOn(): Unit = runBlocking {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, ChronoDatabase::class.java).build()
        val preferences = Preferences(context)
        val night = LocalDateTime.of(2026, 10, 30, 22, 0)
        try {
            val repository = LocalRepository(database)
            repository.saveRule(AppRule("com.video", "Video", maxOpenCount = 3))
            BedtimePrompt.dismiss(context)
            preferences.setBedtime(false)
            BedtimePrompt.onCharging(context, preferences, repository, night)
            assertTrue("off by default", settled(0))

            preferences.setBedtime(true)
            preferences.bedtimeAsked(LocalDate.of(2000, 1, 1))
            BedtimePrompt.onCharging(context, preferences, repository, LocalDateTime.of(2026, 10, 30, 14, 0))
            assertTrue("not in the afternoon", settled(0))
            BedtimePrompt.onCharging(context, preferences, repository, night)
            assertTrue("posted", settled(1))
            assertEquals(LocalDate.of(2026, 10, 30), preferences.bedtimeNight.first())

            BedtimePrompt.dismiss(context)
            assertTrue("dismissed", settled(0))
            BedtimePrompt.onCharging(context, preferences, repository, night.plusHours(3))
            assertTrue("once a night", settled(0))
        } finally {
            BedtimePrompt.dismiss(context)
            preferences.setBedtime(false)
            database.close()
        }
    }

    @Test fun nightFocus_runsUntilMorning_butNotPastTwelveHours(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChronoDatabase::class.java).build()
        try {
            val repository = LocalRepository(database)
            repository.saveRule(AppRule("com.video", "Video", maxOpenCount = 3))
            val now = 1_000_000L
            val morning = now + 9 * 60 * 60_000L
            repository.startFocusUntil(morning, now)
            assertEquals(morning, database.dao().rules().single().focusUntilEpochMillis)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.startFocusUntil(now + 13 * 60 * 60_000L, now) } }
        } finally { database.close() }
    }
}
