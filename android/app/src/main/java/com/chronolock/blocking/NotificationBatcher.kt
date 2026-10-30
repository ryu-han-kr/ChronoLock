package com.chronolock.blocking

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chronolock.data.LocalRepository
import com.chronolock.data.Preferences
import com.chronolock.domain.NotificationBatch
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

/**
 * Holds back notifications from apps with a limit until the next delivery time, by snoozing them: the system shows
 * the original again then. Only the posting app, category and flags are looked at, never the text. Calls, alarms,
 * ongoing notifications and the safety apps (phone, messages) always come through.
 */
@AndroidEntryPoint
class NotificationBatcher : NotificationListenerService() {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var repository: LocalRepository
    @Inject lateinit var environment: AndroidEnvironment
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching: Job? = null
    @Volatile private var on = false
    @Volatile private var limited = emptySet<String>()
    @Volatile private var safety = emptySet<String>()

    override fun onListenerConnected() {
        watching?.cancel()
        watching = scope.launch {
            safety = runCatching { environment.safetyPackages() }.getOrDefault(emptySet())
            try {
                combine(preferences.batchNotifications, repository.rules) { batch, rules -> batch to rules }.collect { (batch, rules) ->
                    on = batch; limited = rules.filter { it.enabled }.map { it.packageName }.toSet()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Notification batching settings unreadable", error) }
        }
    }

    override fun onListenerDisconnected() { watching?.cancel(); watching = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!on || sbn.packageName !in limited || sbn.packageName in safety) return
        val notification = sbn.notification
        if (sbn.isOngoing || !sbn.isClearable || notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (notification.category in ALWAYS) return
        val now = LocalDateTime.now()
        val wait = java.time.Duration.between(now, NotificationBatch.nextDelivery(now)).toMillis()
        runCatching { snoozeNotification(sbn.key, wait) }
            .onFailure { android.util.Log.w("ChronoLock", "Could not hold a notification", it) }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private companion object {
        val ALWAYS = setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT)
    }
}
