package com.chronolock.blocking

import android.Manifest
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri
import com.chronolock.domain.ClockReading
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)
data class UsageRecovery(val dailyMillis: Map<String, Long>, val foregroundPackage: String?)

@Singleton
class AndroidEnvironment @Inject constructor(@ApplicationContext val context: Context) {
    fun clock() = ClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0), ZoneId.systemDefault())

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_DEFAULT &&
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
    }

    fun accessibilityEnabled(): Boolean {
        val component = ComponentName(context, ChronoAccessibilityService::class.java)
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    }

    fun interactive(): Boolean = context.getSystemService(PowerManager::class.java).isInteractive &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    // Package sets are rebuilt at most once a minute: every window event used to cost three PackageManager
    // queries and a Telecom binder call on the main thread.
    private var cachedAt = 0L
    private var cachedSafety: Set<String> = emptySet()
    private var cachedTransient: Set<String> = emptySet()

    @Synchronized private fun refreshPackageSets() {
        if (cachedAt != 0L && SystemClock.elapsedRealtime() - cachedAt < 60_000) return
        cachedSafety = querySafetyPackages()
        cachedTransient = queryTransientPackages()
        launchable.clear()
        cachedAt = SystemClock.elapsedRealtime()
    }

    /** Home, settings, dialer, call screen, messages and this app: leaving to one of these ends the session. Never offered as limit targets. */
    fun safetyPackages(): Set<String> { refreshPackageSets(); return cachedSafety }

    /** Keyboards, permission prompts and system UI: drawn over the current app without ending its session. */
    fun transientPackages(): Set<String> { refreshPackageSets(); return cachedTransient }

    @Suppress("DEPRECATION")
    private fun querySafetyPackages(): Set<String> = buildSet {
        add(context.packageName)
        addAll(listOf("com.android.settings", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller"))
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).forEach { add(it.activityInfo.packageName) }
        pm.queryIntentActivities(Intent(Intent.ACTION_DIAL, "tel:".toUri()), 0).forEach { add(it.activityInfo.packageName) }
        context.getSystemService(TelecomManager::class.java).defaultDialerPackage?.let(::add)
        // The call screen can be its own package (Samsung's incallui), apart from the dialer; a block must not cover a call.
        addAll(IN_CALL_UI)
        pm.queryIntentServices(Intent(android.telecom.InCallService.SERVICE_INTERFACE), PackageManager.GET_META_DATA)
            .filter { it.serviceInfo.metaData?.getBoolean(TelecomManager.METADATA_IN_CALL_SERVICE_UI) == true }
            .forEach { add(it.serviceInfo.packageName) }
        // Texting is as basic as calling: the default SMS app and the stock messaging apps are never blocked.
        Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)
        addAll(MESSAGING)
        pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName?.let(::add)
    }

    private fun queryTransientPackages(): Set<String> = buildSet {
        addAll(listOf("com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller"))
        runCatching { context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { add(it.packageName) } }
    }

    @Suppress("DEPRECATION")
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val safe = safetyPackages()
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
            .distinctBy { it.packageName }.filterNot { it.packageName in safe }.sortedBy { it.label.lowercase() }
    }

    /** Whether a package has a launcher entry; cached with the package sets so window events skip the binder call. */
    private val launchable = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun windowKind(packageName: String): WindowKind = classifyWindow(packageName, transientPackages(), safetyPackages(),
        launchable.getOrPut(packageName) { context.packageManager.getLaunchIntentForPackage(packageName) != null })

    /** Rebuild foreground duration from events, not day-sized queryUsageStats buckets. */
    suspend fun recoverUsage(sinceEpochMillis: Long? = null): UsageRecovery = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext UsageRecovery(emptyMap(), null)
        val now = clock()
        val midnight = now.date.atStartOfDay(now.zone).toInstant().toEpochMilli()
        val cutoff = maxOf(midnight, sinceEpochMillis ?: midnight)
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(midnight - 86400000, now.epochMillis)
        val usage = mutableMapOf<String, Long>()
        var activePackage: String? = null
        var activeClass: String? = null
        var start = midnight
        fun close(at: Long) {
            activePackage?.let { app -> usage[app] = (usage[app] ?: 0) + (at - maxOf(start, cutoff)).coerceAtLeast(0) }
            activePackage = null
            activeClass = null
        }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    close(event.timeStamp)
                    activePackage = event.packageName
                    activeClass = event.className
                    start = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> if (event.packageName == activePackage && event.className == activeClass) close(event.timeStamp)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> close(event.timeStamp)
            }
        }
        val foreground = activePackage.takeIf { interactive() }
        close(now.epochMillis)
        UsageRecovery(usage, foreground)
    }

    private companion object {
        /** Call screens shipped apart from the dialer; also found through their in-call service when the system allows. */
        val IN_CALL_UI = listOf("com.samsung.android.incallui", "com.android.incallui", "com.android.server.telecom")
        val MESSAGING = listOf("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms")
    }
}
