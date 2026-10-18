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
import com.chronolock.data.Preferences
import com.chronolock.domain.MonitorState
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
    private var screenReceiverRegistered = false
    private var started = false
    private sealed interface Command {
        data class Foreground(val packageName: String?) : Command
        data object Tick : Command
        data object Recover : Command
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            commands.trySend(if (intent?.action == Intent.ACTION_SCREEN_OFF) Command.Foreground(null) else Command.Recover)
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
            try { preferences.language.collect { overlay.setLanguage(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not load overlay language", error) }
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
                for (command in commands) {
                    val state = when (command) {
                        is Command.Foreground -> repository.foreground(command.packageName?.takeUnless { it in environment.safetyPackages() }, environment.clock())
                        Command.Tick -> if (environment.interactive()) repository.tick(environment.clock()) else repository.foreground(null, environment.clock())
                        Command.Recover -> repository.foreground(environment.recoverUsage().foregroundPackage?.takeUnless { it in environment.safetyPackages() }, environment.clock())
                    }
                    render(state)
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
        scope.launch { while (isActive) { delay(1000); commands.send(Command.Tick) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val app = event.packageName?.toString() ?: return
        if (::overlay.isInitialized && overlay.visible && app == packageName) return
        if (app == "com.android.systemui") return // transient notification/status windows are not app launches
        if (environment.meaningfulPackage(app)) commands.trySend(Command.Foreground(app))
    }

    private fun render(state: MonitorState) {
        val target = state.observedPackage
        if (target != null && state.decision.denied) {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }.getOrDefault(target)
            overlay.show(label, state.decision)
        } else overlay.hide()
    }

    override fun onInterrupt() { health.disconnected() }
    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.hide()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        commands.close()
        scope.cancel()
        health.disconnected()
        super.onDestroy()
    }
}
