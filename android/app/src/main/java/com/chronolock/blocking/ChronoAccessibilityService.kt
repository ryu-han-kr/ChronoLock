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
    /** Blocked apps let go while only in picture-in-picture; ignored as on-screen until they return to a full window. */
    private val pipReleased = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** The last check found only home or settings on screen while an app was still observed. */
    private var leaveSuspected = false
    private var sites: List<BlockedSite> = emptyList()
    /**
     * The blocked site open in each browser, by package; only its domain is kept, never the address. Kept per browser so a
     * chat beside a browser in split screen cannot clear the browser's block by being checked after it.
     */
    private val blockedSites = mutableMapOf<String, String>()
    private val siteChecks = mutableSetOf<String>()
    private var lastState: MonitorState? = null
    /** The app of the current visit; a passed pause and the next check-in belong to it and reset when it changes. */
    private var visitApp: String? = null
    private var passedApp: String? = null
    private var nextCheckInMillis = 0L
    /** Strict delay and lock as last read; the block screen hides its settings shortcut while either holds. */
    @Volatile private var strict = com.chronolock.domain.StrictSetting()
    /** Lines the person wrote to themselves per app, for its block screen. */
    @Volatile private var notes = emptyMap<String, String>()
    @Volatile private var lock = com.chronolock.domain.LockSetting()
    /** App labels for the block screen; looked up once per app instead of every second while blocked. */
    private val labels = mutableMapOf<String, String>()
    private sealed interface Command {
        data class Foreground(val packageName: String?) : Command
        data object Tick : Command
        data object Recover : Command
        data object ScreenOff : Command
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    beforeScreenOff = observed
                    commands.trySend(Command.ScreenOff)
                }
                // Plugging in at night may ask whether to focus until morning; nothing to do with monitoring.
                Intent.ACTION_POWER_CONNECTED -> scope.launch {
                    runCatching { com.chronolock.data.BedtimePrompt.onCharging(this@ChronoAccessibilityService, preferences, repository) }
                        .onFailure { android.util.Log.w("ChronoLock", "Bedtime prompt failed", it) }
                }
                else -> commands.trySend(Command.Recover)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (started) return
        started = true
        android.util.Log.i("ChronoLock", "Accessibility service connected; restoring usage")
        overlay = BlockOverlay(this, onHome = {
            if (performGlobalAction(GLOBAL_ACTION_HOME)) { blockedSites.clear(); commands.trySend(Command.Foreground(null)) }
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
            try { preferences.mascot.collect { overlay.setMascot(it); surfaces.mascot = it } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not load the character", error) }
        }
        scope.launch {
            try { preferences.notes.collect { notes = it } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not load notes", error) }
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
            try { combine(preferences.strict, preferences.lock) { s, l -> s to l }.collect { (s, l) -> strict = s; lock = l } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not observe strict mode", error) }
        }
        scope.launch {
            try {
                repository.sites.collect { list ->
                    sites = list.map { it.toDomain() }.filter { it.enabled }
                    watchContent(sites.isNotEmpty())
                    (blockedSites.keys + listOfNotNull(observed)).filter(Browsers::watches).forEach(::requestSiteCheck)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not observe blocked sites", error) }
        }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_POWER_CONNECTED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
        scope.launch {
            // A persistent fault (e.g. a full disk) pauses monitoring and retries with back-off instead of ending it for good.
            var backoff = RESTART_MIN_MS
            while (isActive) {
                runMonitoring()
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(RESTART_MAX_MS)
            }
        }
        scope.launch {
            var wasInteractive = true
            var ticks = 0
            while (isActive) {
                delay(1000)
                val interactive = environment.interactive()
                // One tick closes the session when the screen locks; none while it stays off, so no per-second writes.
                if (interactive || wasInteractive) commands.send(Command.Tick)
                if (interactive && ++ticks % RECONCILE_TICKS == 0) reconcileForeground()
                wasInteractive = interactive
            }
        }
    }

    /** Connects the monitor and processes commands; returns after repeated failures so the caller can retry. */
    private suspend fun runMonitoring() {
            try {
                val recovery = environment.recoverUsage(repository.recoveryStartEpochMillis())
                repository.reconnect(environment.clock(), recovery.dailyMillis, recovery.foregroundPackage)
                health.connected(this@ChronoAccessibilityService)
                preferences.recordMonitorBack(System.currentTimeMillis())
                android.util.Log.i("ChronoLock", "Monitoring connected")
                render(repository.tick(environment.clock()))
                var failures = 0
                for (command in commands) {
                    try {
                        val state = when (command) {
                            is Command.Foreground -> repository.foreground(command.packageName?.takeUnless { it in environment.safetyPackages() }, environment.clock())
                            Command.Tick -> if (environment.interactive()) repository.tick(environment.clock()) else repository.screenOff(environment.clock())
                            Command.ScreenOff -> repository.screenOff(environment.clock())
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
                android.util.Log.e("ChronoLock", "Monitoring adapter failed; retrying", error)
                health.error(this@ChronoAccessibilityService)
                overlay.hide()
                surfaces.clearLive()
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val app = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Content changes arrive constantly from every app; only a browser's address bar matters here.
            if (Browsers.watches(app)) requestSiteCheck(app)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (::overlay.isInitialized && overlay.visible && app == packageName) return
        when (environment.windowKind(app)) {
            WindowKind.TRANSIENT, WindowKind.NONE -> Unit // keyboard, permission prompt, status bar: the app underneath stays observed
            WindowKind.LEAVE -> { blockedSites.clear(); commands.trySend(Command.Foreground(limitedWindowApp())) }
            WindowKind.APP -> {
                if (app in pipReleased && pipOnly(app)) return // its small window changing must not bring the block back
                if (Browsers.watches(app)) requestSiteCheck(app)
                commands.trySend(Command.Foreground(limitedWindowApp() ?: app))
            }
        }
    }

    /**
     * Android occasionally drops the window-state event of an app switch (seen right after the service reconnects), which
     * would leave the previous app observed until the next switch. Compares the active application window with what is
     * observed and sends the switch that was missed.
     */
    private fun reconcileForeground() {
        val apps = runCatching {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.map { it.root?.packageName?.toString() }
        }.getOrNull() ?: return
        // A missed return home: only the launcher, settings or this app on screen. It must hold on two checks in a row so
        // the moment between two apps, when the launcher can be alone on screen, is not taken for leaving.
        val left = leftWithoutEvent(observed, apps, environment::windowKind)
        if (left && leaveSuspected) {
            leaveSuspected = false
            blockedSites.clear()
            commands.trySend(Command.Foreground(null))
            return
        }
        leaveSuspected = left
        if (left) return
        val top = runCatching {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root?.packageName?.toString()
        }.getOrNull() ?: return
        if (top == observed || top == packageName || (::overlay.isInitialized && overlay.visible)) return
        if (top in pipReleased && pipOnly(top)) return
        // Only a missed switch into an app; the launcher or taskbar can hold the active window while an app is still on screen.
        if (environment.windowKind(top) == WindowKind.APP) {
            commands.trySend(Command.Foreground(limitedWindowApp() ?: top))
        }
    }

    /**
     * A limited app still visible in any application window: split screen, a pop-up window or picture-in-picture.
     * Observing it keeps its time counted and the block screen up even when another app takes focus beside it.
     */
    private fun limitedWindowApp(): String? = runCatching {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        // Back in a full window, a released app is limited again.
        pipReleased.removeAll { app -> apps.any { !it.isInPictureInPictureMode && it.root?.packageName?.toString() == app } }
        apps.filterNot { it.isInPictureInPictureMode && it.root?.packageName?.toString() in pipReleased }
            .mapNotNull { it.root?.packageName?.toString() }
            .firstOrNull { it != packageName && rules[it]?.enabled == true && it !in environment.safetyPackages() }
    }.getOrNull()

    /** [app] is on screen only as a picture-in-picture window. */
    private fun pipOnly(app: String): Boolean = runCatching {
        val own = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString() == app }
        own.isNotEmpty() && own.all { it.isInPictureInPictureMode }
    }.getOrDefault(false)

    private fun pauseMedia() {
        val audio = getSystemService(android.media.AudioManager::class.java) ?: return
        listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach {
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(it, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
    }

    /** Content-change events wake the service for every app; they are only needed while some site is blocked. */
    private fun watchContent(on: Boolean) {
        val info = serviceInfo ?: return
        val types = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or (if (on) AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else 0)
        if (info.eventTypes != types) { info.eventTypes = types; serviceInfo = info }
    }

    /** Reads the address bar at most once per [SITE_CHECK_MS], always once more after the last change. */
    private fun requestSiteCheck(browser: String) {
        if (!siteChecks.add(browser)) return
        scope.launch {
            // Reading an in-app page walks its web view, so those apps (chats that change constantly) are checked less often.
            delay(if (browser in Browsers.inApp) IN_APP_CHECK_MS else SITE_CHECK_MS)
            siteChecks.remove(browser)
            checkSite(browser)
        }
    }

    /** A blocked site in a browser still on screen beside the observed app (split screen or a pop-up window). */
    private fun visibleBlockedSite(): String? = if (blockedSites.isEmpty()) null else runCatching {
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.isInPictureInPictureMode }
            .firstNotNullOfOrNull { window -> window.root?.packageName?.toString()?.let(blockedSites::get) }
    }.getOrNull()

    /**
     * The browser's address bar. Samsung tablets often report no active-window root (taskbar, pop-up and split views),
     * so the browser's own application window is searched first.
     */
    private fun browserRoots(browser: String): List<AccessibilityNodeInfo> =
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { it.root }
            .filter { it.packageName == browser } + listOfNotNull(rootInActiveWindow)

    private fun addressBar(browser: String): AccessibilityNodeInfo? {
        val id = Browsers.urlBarIds[browser] ?: return null
        return browserRoots(browser).firstNotNullOfOrNull { it.findAccessibilityNodeInfosByViewId(id).firstOrNull() }
    }

    /**
     * The title and link targets of the page in an in-app browser, or null when none is open. Only these are read, never
     * the page text, and at most [MAX_PAGE_NODES] nodes are visited so a long page or chat stays cheap.
     */
    private fun inAppPage(app: String): Pair<String?, List<String>>? {
        val page = browserRoots(app).filter { it.packageName == app }.firstNotNullOfOrNull { root ->
            breadthFirst(root).firstOrNull { it.className == WEB_VIEW }
        } ?: return null
        return page.text?.toString() to breadthFirst(page).mapNotNull { it.extras?.getCharSequence(TARGET_URL)?.toString() }.toList()
    }

    private fun breadthFirst(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        val queue = ArrayDeque(listOf(root))
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_PAGE_NODES) {
            val node = queue.removeFirst()
            yield(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
    }

    /** The blocked site open in [browser]: its domain, "" when none is, or null when it cannot be told. */
    private fun openSite(browser: String, current: List<BlockedSite>): String? {
        val bar = addressBar(browser)
        // While the person types an address the bar holds their input, not the page.
        if (bar != null && bar.isFocused) return null
        bar?.text?.toString()?.let(SiteDomains::hostOf)?.let { return SiteDomains.match(it, current)?.domain.orEmpty() }
        if (browser !in Browsers.inApp) return null // another window is in front, or the bar scrolled away
        // An app whose in-app browser has a bar (KakaoTalk) shows no page without it: its chats are not walked.
        if (bar == null && browser in Browsers.urlBarIds) return ""
        val (title, links) = inAppPage(browser) ?: return "" // the in-app browser is closed
        return Browsers.pageSite(links, title, current)
    }

    private suspend fun checkSite(browser: String) {
        val current = sites
        val match = if (current.isEmpty()) null else {
            // Node queries are binder calls that can stall on heavy pages; keep them off the main thread.
            val open = withContext(Dispatchers.Default) { runCatching { openSite(browser, current) }.getOrNull() } ?: return // keep the last answer
            open.ifEmpty { null }
        }
        if (match == blockedSites[browser]) return
        if (match == null) blockedSites.remove(browser) else blockedSites[browser] = match
        lastState?.let(::render)
    }

    private fun render(state: MonitorState) {
        lastState = state
        observed = state.observedPackage
        val target = state.observedPackage
        if (target != visitApp) {
            visitApp = target; passedApp = null; nextCheckInMillis = 0
            // A browser already on screen when the service starts, or reached through a missed switch, sends no event to check it by.
            if (target != null && Browsers.watches(target)) requestSiteCheck(target)
        }
        val site = target?.let(blockedSites::get) ?: target?.let { visibleBlockedSite() }
        val covered = when {
            // A blocked app left only in picture-in-picture (video apps go there on Home) would keep a full-screen block
            // over everything with no way to close the small window underneath. Pause its playback and let the person go.
            target != null && state.decision.denied && pipOnly(target) -> {
                pipReleased += target
                pauseMedia()
                commands.trySend(Command.Foreground(null))
                overlay.hide(); false
            }
            target != null && state.decision.denied -> {
                val now = environment.clock()
                val resisted = state.clock?.date?.let { state.daily[com.chronolock.domain.DailyKey(target, it)]?.blockedAttempts } ?: 0
                overlay.show(label(target), state.decision, resisted, allowSettings = strict.delayAt(now) == 0 && !lock.lockedAt(now),
                    note = notes[target]) { reason ->
                    scope.launch {
                        runCatching { preferences.recordOpenReason(environment.clock().date, reason) }
                            .onFailure { android.util.Log.w("ChronoLock", "Open reason not saved", it) }
                    }
                }
                true
            }
            site != null -> { overlay.showSite(site); true }
            target != null && intervene(target, state) -> true
            else -> { overlay.hide(); false }
        }
        // "In use" only while the app itself is on screen, not under a block, site or pause screen.
        runCatching { surfaces.update(state, rules, localized, inUse = !covered, lock = lock) }
            .onFailure { android.util.Log.w("ChronoLock", "Could not update reminders or widget", it) }
    }

    private fun label(target: String) = labels.getOrPut(target) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }.getOrDefault(target)
    }

    /**
     * Gentle friction for an allowed app: a breathing pause before it opens, then a check-in every few minutes of use.
     * Returns true while one of them is on screen.
     */
    private fun intervene(target: String, state: MonitorState): Boolean {
        val rule = rules[target]?.takeIf { it.enabled && !it.pausedAt(state.clock?.epochMillis ?: 0) } ?: return false
        val opens = state.clock?.date?.let { state.daily[com.chronolock.domain.DailyKey(target, it)]?.opens } ?: 0
        val session = state.active?.takeIf { it.packageName == target }?.usageMillis ?: 0
        val pause = rule.pauseSeconds
        if (pause != null && passedApp != target) {
            if (overlay.mode != "pause:$target") overlay.showPause(target, label(target), com.chronolock.domain.pauseDelaySeconds(pause, (opens - 1).coerceAtLeast(0)), (opens - 1).coerceAtLeast(0)) {
                passedApp = target; nextCheckInMillis = 0; lastState?.let(::render)
            }
            return true
        }
        val checkIn = rule.checkInMinutes ?: return false
        if (nextCheckInMillis == 0L) nextCheckInMillis = session + checkIn * 60_000L
        if (session < nextCheckInMillis) return false
        if (overlay.mode != "checkin:$target") overlay.showCheckIn(target, label(target), checkIn) {
            nextCheckInMillis = (lastState?.active?.usageMillis ?: session) + checkIn * 60_000L; overlay.hide()
        }
        return true
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
        const val RESTART_MIN_MS = 5_000L
        const val RESTART_MAX_MS = 300_000L
        /** Seconds between checks for a missed app switch. */
        const val RECONCILE_TICKS = 3
        const val IN_APP_CHECK_MS = 1000L
        const val MAX_PAGE_NODES = 600
        const val WEB_VIEW = "android.webkit.WebView"
        const val TARGET_URL = "AccessibilityNodeInfo.targetUrl"
    }
    /**
     * Unbinding while the service is no longer listed as enabled means the user turned detection off (a process kill
     * or a reboot keeps it listed). Kept so Today can say so, including whether a lock or strict mode was running.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        if (!environment.accessibilityEnabled()) {
            val now = environment.clock()
            val locked = lock.lockedAt(now) || strict.delayAt(now) > 0
            runCatching { runBlocking { withTimeoutOrNull(2_000) { preferences.recordMonitorOff(now.epochMillis, locked) } } }
                .onFailure { android.util.Log.w("ChronoLock", "Could not record detection turned off", it) }
        }
        return super.onUnbind(intent)
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
