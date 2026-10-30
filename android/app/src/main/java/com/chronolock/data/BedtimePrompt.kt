package com.chronolock.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chronolock.R
import com.chronolock.blocking.AndroidEnvironment
import com.chronolock.domain.Bedtime
import com.chronolock.feature.forLanguage
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** "Time to sleep?" when the phone is plugged in at night; its button starts a focus until morning. */
internal object BedtimePrompt {
    private const val CHANNEL = "bedtime"
    private const val ID = 0x534C4550
    const val ACTION_START = "com.chronolock.action.BEDTIME_FOCUS"

    suspend fun onCharging(context: Context, preferences: Preferences, repository: LocalRepository, now: LocalDateTime = LocalDateTime.now()) {
        if (!preferences.bedtime.first()) return
        val night = Bedtime.night(now) ?: return
        if (!Bedtime.promptDue(now, preferences.bedtimeNight.first())) return
        // Nothing to hold back, or a focus already running: no question tonight.
        val rules = repository.rules.first()
        val nowMillis = System.currentTimeMillis()
        if (rules.none { it.enabled } || rules.any { (it.focusUntilEpochMillis ?: 0) > nowMillis }) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        preferences.bedtimeAsked(night)
        val localized = context.forLanguage(preferences.language.first())
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.bedtime_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val start = PendingIntent.getBroadcast(context, 2, Intent(context, BedtimeReceiver::class.java).setAction(ACTION_START),
            PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            manager.notify(ID, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(localized.getString(R.string.bedtime_title))
                .setContentText(localized.getString(R.string.bedtime_body))
                .addAction(0, localized.getString(R.string.bedtime_start), start)
                .setContentIntent(start).setAutoCancel(true)
                // Unanswered by morning it has nothing left to offer.
                .setTimeoutAfter(Bedtime.morning(now).let { java.time.Duration.between(now, it).toMillis() }).build())
        }
    }

    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}

/** The bedtime notification's button: a focus until the next morning. */
@AndroidEntryPoint
class BedtimeReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: LocalRepository
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var environment: AndroidEnvironment

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BedtimePrompt.ACTION_START) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            val localized = context.forLanguage(preferences.language.first())
            val message = try {
                val clock = environment.clock()
                val morning = Bedtime.morning(LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.epochMillis), clock.zone))
                repository.startFocusUntil(morning.atZone(clock.zone).toInstant().toEpochMilli(), clock.epochMillis, clock)
                localized.getString(R.string.bedtime_started)
            } catch (error: Exception) {
                android.util.Log.w("ChronoLock", "Bedtime focus failed", error)
                localized.getString(R.string.tile_focus_failed)
            } finally { BedtimePrompt.dismiss(context) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); pending.finish()
            }
        }
    }
}
