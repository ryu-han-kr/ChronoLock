package com.chronolock.blocking

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.data.*
import com.chronolock.feature.UiText
import com.chronolock.feature.forLanguage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Opt-in like [DeviceBlockingTest]: opens a blocked site in each installed supported browser and expects the site overlay. */
class SiteBlockingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.packageName
    private val service get() = "$app/com.chronolock.blocking.ChronoAccessibilityService"
    private lateinit var automation: UiAutomation
    private lateinit var db: ChronoDatabase
    private var originalServices = ""
    private val detail = AppLanguage.values().filter { it != AppLanguage.SYSTEM }.map {
        UiText(context.forLanguage(it))("차단 목록에 추가한 사이트입니다. 주소창의 도메인만 확인하며 방문 기록은 저장하지 않습니다.")
    }

    @Before fun prepare() {
        assumeTrue("Requires isolated verification APK and explicit opt-in",
            app == "com.chronolock.app.verification" &&
                InstrumentationRegistry.getArguments().getString("deviceVerification") == "true")
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        originalServices = shell("settings get secure enabled_accessibility_services").trim().takeUnless { it == "null" }.orEmpty()
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("input keyevent KEYCODE_HOME")
        db = Room.databaseBuilder(context, ChronoDatabase::class.java, "chronolock.db")
            .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5,
                ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9, ChronoDatabase.MIGRATION_9_10).build()
        db.clearAllTables()
        runBlocking { db.dao().saveSite(SiteEntity("youtube.com")) }
        val services = (originalServices.split(':').filter { it.isNotBlank() && it != service } + service).joinToString(":")
        shell("settings put secure enabled_accessibility_services $services")
        shell("settings put secure accessibility_enabled 1")
        SystemClock.sleep(3000)
    }

    @After fun finish() {
        if (!::automation.isInitialized) return
        shell("input keyevent KEYCODE_HOME")
        val others = originalServices.split(':').filter { it.isNotBlank() && it != service }.joinToString(":")
        shell(if (others.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $others")
        if (::db.isInitialized) { db.clearAllTables(); db.close() }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun overlayVisible() = automation.windows.mapNotNull { it.root }.any { root ->
        detail.any { text -> root.findAccessibilityNodeInfosByText(text).any { it.isVisibleToUser } }
    }

    private fun await(timeout: Long, condition: () -> Boolean): Boolean {
        val until = SystemClock.elapsedRealtime() + timeout
        do { if (condition()) return true; SystemClock.sleep(200) } while (SystemClock.elapsedRealtime() < until)
        return false
    }

    private fun open(browser: String, url: String) {
        shell("am start -W -a android.intent.action.VIEW -d $url -p $browser")
    }

    private fun capture(name: String) {
        val file = File(context.getExternalFilesDir(null), "device-verification/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun limitedAppInAPopUpWindow_staysBlocked_whenAnotherAppIsOpened() {
        val installed = shell("pm list packages").lines().map { it.removePrefix("package:").trim() }.toSet()
        assumeTrue("Chrome and Samsung Internet needed", "com.android.chrome" in installed && "com.sec.android.app.sbrowser" in installed)
        // An all-day schedule blocks Chrome at once.
        runBlocking { db.dao().saveRule(RuleEntity("com.android.chrome", "Chrome", null, null, null, schedules = "0-1439-127")) }
        val services = shell("settings get secure enabled_accessibility_services").trim()
        shell("settings put secure enabled_accessibility_services " + services.split(':').filter { it != service }.joinToString(":").ifEmpty { "null" })
        SystemClock.sleep(1500)
        shell("settings put secure enabled_accessibility_services $services")
        SystemClock.sleep(3000)
        val scheduleText = AppLanguage.values().filter { it != AppLanguage.SYSTEM }.map { UiText(context.forLanguage(it))("쓰지 않기로 정한 시간대예요.") }
        fun blocked() = automation.windows.mapNotNull { it.root }.any { root -> scheduleText.any { t -> root.findAccessibilityNodeInfosByText(t).any { it.isVisibleToUser } } }
        // Chrome in a pop-up (freeform) window, then another app full screen beside it.
        shell("am start -W --windowingMode 5 -a android.intent.action.VIEW -d https://example.com -p com.android.chrome")
        SystemClock.sleep(2000)
        shell("am start -W --windowingMode 1 -a android.intent.action.VIEW -d https://example.org -p com.sec.android.app.sbrowser")
        val shown = await(10_000, ::blocked)
        capture("popup-blocked")
        shell("am force-stop com.android.chrome")
        shell("input keyevent KEYCODE_HOME")
        assertTrue("block screen missing while Chrome stays in a pop-up window", shown)
    }

    @Test fun limitedApp_showsALiveNotification_untilItIsLeft() {
        val installed = shell("pm list packages").lines().map { it.removePrefix("package:").trim() }.toSet()
        assumeTrue("Chrome not installed", "com.android.chrome" in installed)
        shell("pm grant $app android.permission.POST_NOTIFICATIONS")
        runBlocking { db.dao().saveRule(RuleEntity("com.android.chrome", "Chrome", dailyLimitSeconds = 3600, maxOpenCount = 50, cooldownSeconds = null)) }
        // This test writes through its own database connection, which the running service does not observe; reconnect it.
        val services = shell("settings get secure enabled_accessibility_services").trim()
        shell("settings put secure enabled_accessibility_services " + services.split(':').filter { it != service }.joinToString(":").ifEmpty { "null" })
        SystemClock.sleep(1500)
        shell("settings put secure enabled_accessibility_services $services")
        SystemClock.sleep(3000)
        open("com.android.chrome", "https://example.com")
        fun liveShown() = shell("dumpsys notification --noredact").lines().any { it.contains("pkg=$app") && it.contains("live_usage") }
        val shown = await(15_000, ::liveShown)
        capture("live-notification")
        assertTrue("live notification missing", shown)
        shell("input keyevent KEYCODE_HOME")
        assertTrue("live notification stayed after leaving", await(10_000) { !liveShown() })
    }

    @Test fun blockedSite_showsOverlay_andOtherSitesDoNot() {
        val installed = shell("pm list packages").lines().map { it.removePrefix("package:").trim() }.toSet()
        val browsers = listOf("com.android.chrome", "com.sec.android.app.sbrowser").filter { it in installed }
        assumeTrue("No supported browser installed", browsers.isNotEmpty())
        val failures = mutableListOf<String>()
        browsers.forEach { browser ->
            open(browser, "https://example.com")
            SystemClock.sleep(4000)
            capture("site-allowed-$browser")
            if (overlayVisible()) failures += "$browser: overlay on example.com"
            open(browser, "https://www.youtube.com")
            val shown = await(15_000, ::overlayVisible)
            capture("site-blocked-$browser")
            if (!shown) failures += "$browser: no overlay on youtube.com"
            shell("input keyevent KEYCODE_HOME")
            if (!await(5_000) { !overlayVisible() }) failures += "$browser: overlay stayed after home"
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
