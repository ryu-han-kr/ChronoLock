package com.chronolock.blocking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.chronolock.MainActivity
import com.chronolock.data.LocalRepository
import com.chronolock.data.toDomain
import com.chronolock.data.Preferences
import com.chronolock.domain.AppRule
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
        overlay = BlockOverlay(this, onHome = {
            if (performGlobalAction(GLOBAL_ACTION_HOME)) commands.trySend(Command.Foreground(null))
        }, onSettings = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            commands.trySend(Command.Foreground(null))
        })
        scope.launch {
            try { preferences.theme.collect { overlay.setTheme(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not load overlay theme", error) }
        }
        scope.launch {
            try { preferences.language.collect { overlay.setLanguage(it); localized = forLanguage(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not load overlay language", error) }
        }
        scope.launch {
            try { repository.rules.collect { list -> rules = list.associate { it.packageName to it.toDomain() } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not observe rules", error) }
        }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
        scope.launch {
            try {
                val recovery = environment.recoverUsage(repository.recoveryStartEpochMillis())
                repository.reconnect(environment.clock(), recovery.dailyMillis, recovery.foregroundPackage)
                health.connected()
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
                health.error()
                overlay.hide()
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
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val app = event.packageName?.toString() ?: return
        if (::overlay.isInitialized && overlay.visible && app == packageName) return
        when (environment.windowKind(app)) {
            WindowKind.TRANSIENT, WindowKind.NONE -> Unit // keyboard, permission prompt, status bar: the app underneath stays observed
            WindowKind.LEAVE -> commands.trySend(Command.Foreground(null))
            WindowKind.APP -> commands.trySend(Command.Foreground(app))
        }
    }

    private fun render(state: MonitorState) {
        observed = state.observedPackage
        runCatching { surfaces.update(state, rules, localized) }
            .onFailure { android.util.Log.w("ChronoLock", "Could not update reminders or widget", it) }
        val target = state.observedPackage
        if (target != null && state.decision.denied) {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }.getOrDefault(target)
            overlay.show(label, state.decision)
        } else overlay.hide()
    }

    override fun onInterrupt() { health.disconnected() }

    private companion object {
        const val MAX_FAILURES = 5
        const val RECOVER_DELAY_MS = 400L
    }
    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.hide()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        commands.close()
        scope.cancel()
        health.disconnected()
        super.onDestroy()
    }
}
