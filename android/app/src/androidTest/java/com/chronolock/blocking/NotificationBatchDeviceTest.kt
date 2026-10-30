package com.chronolock.blocking

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.data.ChronoDatabase
import com.chronolock.data.Preferences
import com.chronolock.data.RuleEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in like [DeviceBlockingTest]: with batching on and notification access granted, a notification from an app
 * with a limit leaves the shade until the next delivery time; without batching it stays. The shell posts the
 * notifications, as ChronoLock itself is a safety app and the test process cannot post for another package.
 */
class NotificationBatchDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val listener get() = "${context.packageName}/com.chronolock.blocking.NotificationBatcher"
    private val sender = "com.android.shell"

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    /** The key of the shell's notification with [tag] while it is in the shade, or null. */
    private fun shown(tag: String) = shell("cmd notification list").lines().firstOrNull { it.contains("|$sender|") && it.contains("|$tag|") }?.trim()

    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < until) { if (condition()) return; SystemClock.sleep(100) }
        throw AssertionError("Timed out: $message")
    }

    @Test fun limitedAppsNotification_isHeld() {
        assumeTrue("Requires the verification APK and explicit opt-in", context.packageName == "com.chronolock.app.verification" &&
            InstrumentationRegistry.getArguments().getString("deviceVerification") == "true")
        val db = Room.databaseBuilder(context, ChronoDatabase::class.java, "chronolock.db").build()
        val preferences = Preferences(context)
        try {
            runBlocking {
                db.dao().saveRule(RuleEntity(sender, "Shell", null, 50, null))
                preferences.setBatchNotifications(true)
            }
            // Access is granted last, so the listener connects with the rule and the switch already stored.
            shell("cmd notification allow_listener $listener")
            SystemClock.sleep(1500)
            shell("cmd notification post -t ChronoLockBatch chronobatch1 held")
            SystemClock.sleep(300)
            await("held notification leaves the shade") { shown("chronobatch1") == null }

            runBlocking { preferences.setBatchNotifications(false) }
            SystemClock.sleep(500)
            shell("cmd notification post -t ChronoLockBatch chronobatch2 stays")
            SystemClock.sleep(1500)
            assertTrue("without batching it stays", shown("chronobatch2") != null)
        } finally {
            shell("cmd notification disallow_listener $listener")
            // Bring the held one back now rather than at the next delivery time.
            shell("dumpsys notification").lines().map { it.trim() }.filter { it.startsWith("NotificationRecord") || it.contains("key=") }
                .mapNotNull { Regex("key=(\\S*chronobatch1\\S*)").find(it)?.groupValues?.get(1) }.distinct()
                .forEach { shell("cmd notification unsnooze $it") }
            runBlocking { db.dao().deleteRule(sender); preferences.setBatchNotifications(false) }
            db.close()
        }
    }
}
