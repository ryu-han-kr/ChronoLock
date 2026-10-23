package com.chronolock.blocking

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.data.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

/** Opt-in only: run with -PdeviceVerification=true and -e deviceVerification true. */
class DeviceBlockingTest {
    @get:Rule val testName = TestName()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.packageName
    private val target get() = instrumentation.context.packageName
    private val service get() = "$app/com.chronolock.blocking.ChronoAccessibilityService"
    private lateinit var automation: UiAutomation
    private lateinit var db: ChronoDatabase
    private var originalServices = ""
    private var originalAirplane = "false"
    private val rebootStage get() = InstrumentationRegistry.getArguments().getString("rebootStage")

    @Before fun prepare() {
        assumeTrue("Requires isolated verification APK and explicit opt-in",
            app == "com.chronolock.app.verification" &&
                InstrumentationRegistry.getArguments().getString("deviceVerification") == "true")
        if (testName.methodName == "rebootCheckpoint") assumeTrue("Run seed and verify around adb reboot", rebootStage in listOf("seed", "verify"))
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        originalServices = shell("settings get secure enabled_accessibility_services").trim().takeUnless { it == "null" }.orEmpty()
        originalAirplane = shell("settings get global airplane_mode_on").trim()
        disconnect()
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("input keyevent KEYCODE_HOME")
        shell("appops set $app GET_USAGE_STATS allow")
        db = Room.databaseBuilder(context, ChronoDatabase::class.java, "chronolock.db")
            .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4, ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9).build()
        if (rebootStage != "verify") {
            db.clearAllTables()
            // Without a checkpoint the service recovers usage since midnight, including earlier scenarios.
            val now = AndroidEnvironment(context).clock()
            runBlocking { db.dao().saveCheckpoint(CheckpointEntity(activePackage = null, sessionMillis = 0,
                epochMillis = now.epochMillis - 1, elapsedMillis = now.elapsedMillis, bootId = now.bootId, zone = now.zone.id)) }
        }
    }

    @After fun finish() {
        if (!::automation.isInitialized) return
        if (testName.methodName == "rebootCheckpoint" && rebootStage == "seed") {
            db.close()
            return // The explicit reboot driver continues with the same database and enabled service.
        }
        disconnect()
        shell("cmd connectivity airplane-mode ${if (originalAirplane == "1") "enable" else "disable"}")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("input keyevent KEYCODE_HOME")
        shell("appops set $app GET_USAGE_STATS allow")
        if (::db.isInitialized) { db.clearAllTables(); db.close() }
        setServices(originalServices.split(':').filter { it != service }.joinToString(":"))
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    // UiAutomation executes argv directly; shell quotes would become part of a component name.
    private fun setServices(value: String) {
        shell(if (value.isEmpty()) "settings delete secure enabled_accessibility_services"
            else "settings put secure enabled_accessibility_services $value")
    }

    private fun disconnect() {
        val others = originalServices.split(':').filter { it.isNotBlank() && it != service }.joinToString(":")
        setServices(others)
        SystemClock.sleep(1000)
    }

    private fun connect() {
        val started = System.currentTimeMillis()
        val services = (originalServices.split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")
        setServices(services)
        shell("settings put secure accessibility_enabled 1")
        assertEquals(services, shell("settings get secure enabled_accessibility_services").trim())
        await("service starts persisting checkpoints") { checkpoint()?.epochMillis?.let { it >= started } == true }
    }

    private fun rule(daily: Long? = null, opens: Int? = null, cooldown: Long? = null, session: Long? = null) {
        runBlocking { db.dao().saveRule(RuleEntity(target, "Device verification", daily, opens, cooldown, sessionLimitSeconds = session)) }
        connect()
    }

    private fun launch(detail: Boolean = false) {
        val activity = if (detail) "VerificationDetailActivity" else "VerificationTargetActivity"
        shell("am start -W -n $target/com.chronolock.blocking.$activity")
        await("target observed") { checkpoint()?.observedPackage == target }
    }

    private fun home() {
        shell("input keyevent KEYCODE_HOME")
        await("home leaves target") { checkpoint()?.observedPackage == null }
    }

    private fun checkpoint() = runBlocking { db.dao().checkpoint() }
    private fun daily() = runBlocking { db.dao().daily().firstOrNull { it.packageName == target && it.date == LocalDate.now().toString() } }
    private fun closed() = runBlocking { db.dao().closes().first { it.packageName == target } }

    private fun await(message: String, timeout: Long = 10_000, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        do {
            if (condition()) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < until)
        fail("Timed out: $message; checkpoint=${checkpoint()}; daily=${daily()}")
    }

    private fun nodes(): List<AccessibilityNodeInfo> = automation.windows.mapNotNull { it.root }
    private fun textVisible(text: String) = nodes().any { it.findAccessibilityNodeInfosByText(text).any { node -> node.isVisibleToUser } }
    private fun blocked(reason: String) {
        await("blocked checkpoint") { checkpoint()?.wasBlocked == true }
        await("visible overlay: $reason") { textVisible(reason) }
    }
    private fun capture(name: String) {
        val file = File(context.getExternalFilesDir(null), "device-verification/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun daily60Seconds_blocksOffline_andKeepsBlockingOnReturn() {
        shell("cmd connectivity airplane-mode enable")
        rule(daily = 60)
        val start = SystemClock.elapsedRealtime()
        launch()
        await("60 second daily limit", 65_000) { checkpoint()?.wasBlocked == true }
        val elapsed = SystemClock.elapsedRealtime() - start
        assertTrue("daily usage reaches 60s", daily()!!.usageMillis >= 60_000)
        assertTrue("must not block early: $elapsed", elapsed >= 58_000)
        assertTrue("blocking latency: $elapsed", elapsed <= 65_000)
        blocked("오늘 정한 사용 시간을 다 썼어요.")
        android.util.Log.i("ChronoDeviceTest", "daily60 observed elapsedMs=$elapsed usageMs=${daily()!!.usageMillis}")
        capture("daily60-offline")
        home(); launch(); blocked("오늘 정한 사용 시간을 다 썼어요.")
    }

    @Test fun fourthOpen_blocks_butSameAppActivityDoesNotCountAgain() {
        rule(opens = 3)
        repeat(3) { index ->
            launch()
            assertFalse(checkpoint()!!.wasBlocked)
            assertEquals(index + 1, daily()!!.opens)
            if (index < 2) home()
        }
        launch(detail = true)
        SystemClock.sleep(1200)
        assertEquals(3, daily()!!.opens)
        assertFalse(checkpoint()!!.wasBlocked)
        home(); launch()
        blocked("오늘 허용한 실행 횟수를 다 썼어요.")
        assertEquals(3, daily()!!.opens)
        capture("fourth-open")
    }

    @Test fun cooldown30Seconds_retriesDoNotExtendDeadline() {
        rule(cooldown = 30)
        launch(); home()
        val mark = closed()
        SystemClock.sleep(10_000)
        launch(); blocked("다시 열기까지")
        home(); launch(); blocked("다시 열기까지")
        assertEquals(mark.epochMillis, closed().epochMillis)
        capture("cooldown")
        await("cooldown expires", 32_000) { checkpoint()?.wasBlocked == false }
        assertTrue(System.currentTimeMillis() - mark.epochMillis < 34_000)
    }

    @Test fun sessionLimit_survivesAccessibilityReconnect_andSettingsRemainReachable() {
        rule(session = 3)
        launch(); blocked("이번에 쓸 수 있는 시간을 다 썼어요.")
        disconnect()
        await("overlay removed on disconnect") { !textVisible("지금은 쉬는 시간이에요") }
        connect()
        blocked("이번에 쓸 수 있는 시간을 다 썼어요.")
        val button = nodes().flatMap { it.findAccessibilityNodeInfosByText("내 제한 설정 보기") }.first { it.isClickable }
        assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("settings action leaves blocked target") { checkpoint()?.observedPackage == null }
        assertFalse(textVisible("지금은 쉬는 시간이에요"))
    }

    @Test fun screenLock_doesNotAccumulateUsage() {
        rule(daily = 600)
        launch()
        await("initial usage") { (daily()?.usageMillis ?: 0) >= 2000 }
        shell("input keyevent KEYCODE_SLEEP")
        await("screen off closes active session") { checkpoint()?.activePackage == null }
        val before = daily()!!.usageMillis
        SystemClock.sleep(5000)
        assertEquals(before, daily()!!.usageMillis)
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        launch()
        await("usage resumes") { daily()!!.usageMillis > before }
    }

    @Test fun revokingUsagePermission_isDetected_andRestoringItRecovers() {
        rule(daily = 600)
        launch()
        shell("appops set $app GET_USAGE_STATS deny")
        assertFalse(AndroidEnvironment(context).hasUsageAccess())
        home()
        shell("am start -W -a android.settings.SETTINGS")
        assertFalse(checkpoint()!!.wasBlocked)
        shell("appops set $app GET_USAGE_STATS allow")
        assertTrue(AndroidEnvironment(context).hasUsageAccess())
        disconnect(); connect(); launch()
        await("usage after permission restored") { (daily()?.usageMillis ?: 0) > 0 }
    }

    @Test fun pauseBeforeOpening_countsDown_thenOpensWithoutBlocking() {
        runBlocking { db.dao().saveRule(RuleEntity(target, "Device verification", null, null, null, pauseSeconds = 3)) }
        connect()
        launch()
        await("pause shown") { textVisible("숨 한 번 쉬어요") }
        capture("pause-before-opening")
        assertFalse(checkpoint()!!.wasBlocked)
        assertTrue(textVisible("그래도 열기 ("))
        await("countdown ends", 8_000) { !textVisible("그래도 열기 (") }
        val button = nodes().flatMap { it.findAccessibilityNodeInfosByText("그래도 열기") }.first { it.isClickable && it.isEnabled }
        assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("pause dismissed") { !textVisible("숨 한 번 쉬어요") }
        await("usage counted after opening") { (daily()?.usageMillis ?: 0) > 1000 }
    }

    @Test fun deletingRule_removesActiveOverlay() {
        rule(session = 2)
        launch(); blocked("이번에 쓸 수 있는 시간을 다 썼어요.")
        runBlocking { db.dao().deleteRule(target) }
        await("rule removal unblocks") { checkpoint()?.wasBlocked == false }
        await("overlay hidden") { !textVisible("지금은 쉬는 시간이에요") }
    }

    @Test fun rebootCheckpoint() {
        val preferences = context.getSharedPreferences("reboot-verification", 0)
        val clock = AndroidEnvironment(context).clock()
        if (rebootStage == "seed") {
            runBlocking { db.dao().saveDaily(listOf(DailyEntity(target, clock.date.toString(), 60_000, 3, 0))) }
            rule(daily = 60, opens = 3, cooldown = 30)
            launch(); blocked("오늘 정한 사용 시간을 다 썼어요.")
            assertTrue(preferences.edit().putInt("bootId", clock.bootId).putString("date", clock.date.toString()).commit())
            capture("before-reboot")
        } else {
            assertTrue("A real device reboot must occur between stages", clock.bootId > preferences.getInt("bootId", Int.MAX_VALUE))
            assertEquals("Run both stages on the same local date", clock.date.toString(), preferences.getString("date", null))
            val saved = runBlocking { db.dao().rules().single() }
            assertEquals(60L, saved.dailyLimitSeconds)
            assertEquals(3, saved.maxOpenCount)
            assertTrue(daily()!!.usageMillis >= 60_000)
            assertEquals(3, daily()!!.opens)
            connect(); launch(); blocked("오늘 정한 사용 시간을 다 썼어요.")
            assertEquals(clock.bootId, checkpoint()!!.bootId)
            capture("after-reboot")
            preferences.edit().clear().commit()
        }
    }
}
