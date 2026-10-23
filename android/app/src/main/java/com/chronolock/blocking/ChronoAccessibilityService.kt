package com.chronolock.blocking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.chronolock.MainActivity
import com.chronolock.data.LocalRepository
import com.chronolock.data.toDomain
import com.chronolock.data.Preferences
import com.chronolock.domain.AppRule
import com.chronolock.domain.BlockedSite
import com.chronolock.domain.Browsers
import com.chronolock.domain.SiteDomains
import com.chronolock.domain.withGroups
import kotlinx.coroutines.flow.combine
import com.chronolock.domain.MonitorState
import com.chronolock.feature.forLanguage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect

@AndroidEntryPoint
class ChronoAccessibilityService : AccessibilityService() {
    @Inject lateinit var repository: LocalRepository
    @Inject lateinit var environment: AndroidEnvironment
    @Inject lateinit var health: MonitorHealth
    @Inject lateinit var preferences: Preferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private lateinit var overlay: BlockOverlay
    private val surfaces by lazy { LimitSurfaces(applicationContext) }
    private var rules: Map<String, AppRule> = emptyMap()
    private var localized: android.content.Context = this
    private var screenReceiverRegistered = false
    private var started = false
    /** The app on screen before the screen turned off; re-observed after unlock when usage events cannot tell. */
    private var observed: String? = null
    private var beforeScreenOff: String? = null
    private var sites: List<BlockedSite> = emptyList()
    /** The blocked site open in [siteBrowser]; only its domain is kept, never the address. */
    private var blockedSite: String? = null
    private var siteBrowser: String? = null
    private var siteCheckScheduled = false
    private var lastState: MonitorState? = null
    private sealed interface Command {
        data class Foreground(val packageName: String?) : Command
        data object Tick : Command
        data object Recover : Command
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                beforeScreenOff = observed
                commands.trySend(Command.Foreground(null))
            } else commands.trySend(Command.Recover)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (started) return
        started = true
        android.util.Log.i("ChronoLock", "Accessibility service connected; restoring usage")
        overlay = BlockOverlay(this, onHome = {
            if (performGlobalAction(GLOBAL_ACTION_HOME)) { blockedSite = null; commands.trySend(Command.Foreground(null)) }
        }, onSettings = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            commands.trySend(Command.Foreground(null))
        })
        scope.launch {
            try {
                combine(preferences.theme, preferences.nightTheme, preferences.followSystem) { day, night, follow -> Triple(day, night, follow) }
                    .collect { themes = it; applyOverlayTheme() }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not load overlay theme", error) }
        }
        scope.launch {
            try { preferences.language.collect { overlay.setLanguage(it); localized = forLanguage(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not load overlay language", error) }
        }
        scope.launch {
            // Groups are attached as the monitor sees them, so the live notification can show shared limits too.
            try {
                repository.rules.combine(repository.groups) { list, groups ->
                    withGroups(list.associate { it.packageName to it.toDomain() }, groups.map { it.toDomain() })
                }.collect { rules = it }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not observe rules", error) }
        }
        scope.launch {
            try {
                repository.sites.collect { list ->
                    sites = list.map { it.toDomain() }.filter { it.enabled }
                    siteBrowser?.let(::requestSiteCheck)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not observe blocked sites", error) }
        }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
        scope.launch {
            try {
                val recovery = environment.recoverUsage(repository.recoveryStartEpochMillis())
                repository.reconnect(environment.clock(), recovery.dailyMillis, recovery.foregroundPackage)
                health.connected(this@ChronoAccessibilityService)
                android.util.Log.i("ChronoLock", "Monitoring connected")
                render(repository.tick(environment.clock()))
                var failures = 0
                for (command in commands) {
                    try {
                        val state = when (command) {
                            is Command.Foreground -> repository.foreground(command.packageName?.takeUnless { it in environment.safetyPackages() }, environment.clock())
                            Command.Tick -> if (environment.interactive()) repository.tick(environment.clock()) else repository.foreground(null, environment.clock())
                            Command.Recover -> {
                                delay(RECOVER_DELAY_MS) // The resumed activity's usage event lands shortly after USER_PRESENT.
                                // Without usage access the recovery is always empty, so fall back to what was on screen before the lock.
                                val recovered = environment.recoverUsage().foregroundPackage ?: beforeScreenOff
                                repository.foreground(recovered?.takeUnless { it in environment.safetyPackages() }, environment.clock())
                            }
                        }
                        failures = 0
                        render(state)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        // One failed overlay draw or database write must not end monitoring; a persistent fault does.
                        if (++failures >= MAX_FAILURES) throw error
                        android.util.Log.w("ChronoLock", "Monitoring step failed ($failures/$MAX_FAILURES)", error)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.e("ChronoLock", "Monitoring adapter failed", error)
                health.error(this@ChronoAccessibilityService)
                overlay.hide()
                surfaces.clearLive()
                commands.close()
                scope.coroutineContext.cancelChildren()
            }
        }
        scope.launch {
            var wasInteractive = true
            while (isActive) {
                delay(1000)
                val interactive = environment.interactive()
                // One tick closes the session when the screen locks; none while it stays off, so no per-second writes.
                if (interactive || wasInteractive) commands.send(Command.Tick)
                wasInteractive = interactive
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val app = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Content changes arrive constantly from every app; only a browser's address bar matters here.
            if (app in Browsers.urlBarIds) requestSiteCheck(app)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (::overlay.isInitialized && overlay.visible && app == packageName) return
        when (environment.windowKind(app)) {
            WindowKind.TRANSIENT, WindowKind.NONE -> Unit // keyboard, permission prompt, status bar: the app underneath stays observed
            WindowKind.LEAVE -> { blockedSite = null; commands.trySend(Command.Foreground(null)) }
            WindowKind.APP -> {
                if (app != siteBrowser) blockedSite = null
                if (app in Browsers.urlBarIds) requestSiteCheck(app)
                commands.trySend(Command.Foreground(app))
            }
        }
    }

    /** Reads the address bar at most once per [SITE_CHECK_MS], always once more after the last change. */
    private fun requestSiteCheck(browser: String) {
        siteBrowser = browser
        if (siteCheckScheduled) return
        siteCheckScheduled = true
        scope.launch {
            delay(SITE_CHECK_MS)
            siteCheckScheduled = false
            siteBrowser?.let(::checkSite)
        }
    }

    /**
     * The browser's address bar. Samsung tablets often report no active-window root (taskbar, pop-up and split views),
     * so the browser's own application window is searched first.
     */
    private fun addressBar(browser: String): AccessibilityNodeInfo? {
        val id = Browsers.urlBarIds.getValue(browser)
        val roots = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { it.root }
            .filter { it.packageName == browser } + listOfNotNull(rootInActiveWindow)
        return roots.firstNotNullOfOrNull { it.findAccessibilityNodeInfosByViewId(id).firstOrNull() }
    }

    private fun checkSite(browser: String) {
        val match = if (sites.isEmpty()) null else {
            val bar = runCatching { addressBar(browser) }
                .getOrNull() ?: return // another window is in front, or the bar scrolled away: keep the last answer
            // While the person types an address the bar holds their input, not the page.
            if (bar.isFocused) return
            val host = SiteDomains.hostOf(bar.text?.toString().orEmpty()) ?: return
            SiteDomains.match(host, sites)?.domain
        }
        if (match == blockedSite) return
        blockedSite = match
        lastState?.let(::render)
    }

    private fun render(state: MonitorState) {
        lastState = state
        observed = state.observedPackage
        runCatching { surfaces.update(state, rules, localized) }
            .onFailure { android.util.Log.w("ChronoLock", "Could not update reminders or widget", it) }
        val target = state.observedPackage
        if (target != null && state.decision.denied) {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }.getOrDefault(target)
            overlay.show(label, state.decision)
        } else {
            val site = blockedSite
            if (site != null && target != null && target == siteBrowser) overlay.showSite(site) else overlay.hide()
        }
    }

    // Called when feedback should stop (e.g. another service speaks); this service gives no feedback and keeps running.
    override fun onInterrupt() = Unit

    /** Day, night and whether to follow the device's dark mode, as last read from preferences. */
    private var themes: Triple<com.chronolock.data.AppTheme, com.chronolock.data.AppTheme, Boolean>? = null
    private fun applyOverlayTheme() {
        val (day, night, follow) = themes ?: return
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (::overlay.isInitialized) overlay.setTheme(if (follow && dark) night else day)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOverlayTheme()
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val RECOVER_DELAY_MS = 400L
        const val SITE_CHECK_MS = 300L
    }
    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.hide()
        surfaces.clearLive()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        commands.close()
        scope.cancel()
        health.disconnected(this)
        super.onDestroy()
    }
}
