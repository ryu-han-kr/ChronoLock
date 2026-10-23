package com.chronolock.blocking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chronolock.MainActivity
import com.chronolock.R
import com.chronolock.domain.AppRule
import com.chronolock.domain.DailyKey
import com.chronolock.domain.MonitorState
import com.chronolock.domain.TodaySummary
import com.chronolock.domain.LiveLimit
import com.chronolock.domain.limitWarning
import com.chronolock.domain.liveLimit
import com.chronolock.domain.remainingDailyMillis
import com.chronolock.domain.todaySummary
import com.chronolock.feature.UiText
import com.chronolock.feature.formatDuration

/**
 * Glanceable outputs of the monitor: an ongoing notification with what is left of the limits of the app on screen,
 * a once-a-day "limit nearly used" notification and the home-screen widget.
 * [localized] must already carry the app's chosen language.
 */
internal class LimitSurfaces(private val context: Context) {
    private val prefs = context.getSharedPreferences("limit_surfaces", Context.MODE_PRIVATE)
    // Persisted so a service restart does not repeat the once-a-day warning.
    private val warned: MutableSet<DailyKey> = prefs.getString(KEY_WARNED_DATE, null)?.let { date ->
        runCatching { java.time.LocalDate.parse(date) }.getOrNull()?.let { day ->
            prefs.getStringSet(KEY_WARNED_APPS, emptySet()).orEmpty().mapTo(mutableSetOf()) { DailyKey(it, day) }
        }
    } ?: mutableSetOf()
    private var lastSummary: TodaySummary? = null
    private var lastLive: String? = null

    init { clearLive() } // A notification left by a stopped service must not outlive it.

    /** Removes the live notification; the service calls this when it stops. */
    fun clearLive() { lastLive = null; runCatching { NotificationManagerCompat.from(context).cancel(LIVE_ID) } }

    fun update(state: MonitorState, rules: Map<String, AppRule>, localized: Context) {
        showLive(state.liveLimit(rules), state.clock?.epochMillis ?: System.currentTimeMillis(), localized)
        state.clock?.date?.let { today -> warned.removeAll { it.date != today } }
        state.limitWarning(rules, warned)?.let { key ->
            warned += key
            prefs.edit().putString(KEY_WARNED_DATE, key.date.toString()).putStringSet(KEY_WARNED_APPS, warned.map { it.packageName }.toSet()).apply()
            val rule = rules.getValue(key.packageName)
            notify(localized, rule, state.remainingDailyMillis(rule) ?: 0)
        }
        // Minute resolution keeps widget redraws to about one per minute of use.
        val summary = state.todaySummary(rules).let {
            it.copy(usedMillis = it.usedMillis / 60_000 * 60_000, tightestRemainingMillis = it.tightestRemainingMillis?.let { ms -> (ms + 59_999) / 60_000 * 60_000 })
        }
        if (summary != lastSummary) {
            lastSummary = summary
            TodayWidget.store(context, summary, localized)
        }
    }

    private fun notify(localized: Context, rule: AppRule, remainingMillis: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.limit_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val text = UiText(localized)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_chronolock)
            .setContentTitle(localized.getString(R.string.limit_warning_title, rule.appName))
            .setContentText(localized.getString(R.string.limit_warning_text, formatDuration((remainingMillis + 999) / 1000, text)))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(rule.packageName.hashCode(), notification) }
    }

    /**
     * Minute-rounded text redraws about once a minute; the seconds tick in the notification's own count-down chronometer,
     * so the monitor never posts once per second.
     */
    private fun showLive(live: LiveLimit?, now: Long, localized: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (live == null) {
            if (lastLive != null) { lastLive = null; runCatching { manager.cancel(LIVE_ID) } }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = UiText(localized)
        fun left(millis: Long) = formatDuration(if (millis >= 60_000) (millis + 59_999) / 60_000 * 60 else (millis + 999) / 1000, text)
        val res = localized.resources
        val lines = buildList {
            live.dailyLeftMillis?.let { add(localized.getString(R.string.live_daily, formatDuration(live.dailyLimitMillis!! / 1000, text), left(it))) }
            live.sessionLeftMillis?.let { add(localized.getString(R.string.live_session, left(it))) }
            live.opensLeft?.let { add(res.getQuantityString(R.plurals.live_opens, it, it)) }
            live.groups.forEach { group ->
                group.timeLeftMillis?.let { add(localized.getString(R.string.live_group_time, group.name, left(it))) }
                group.opensLeft?.let { add(res.getQuantityString(R.plurals.live_group_opens, it, group.name, it)) }
            }
        }
        val title = localized.getString(R.string.live_title, live.appName)
        val key = live.packageName + "|" + lines.joinToString("|")
        if (key == lastLive) return
        lastLive = key
        manager.createNotificationChannel(NotificationChannel(LIVE_CHANNEL, localized.getString(R.string.live_channel), NotificationManager.IMPORTANCE_LOW))
        val builder = NotificationCompat.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_chronolock)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openApp(context))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        live.nearestMillis?.let { builder.setWhen(now + it).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true) }
        runCatching { manager.notify(LIVE_ID, builder.build()) }
    }

    private companion object {
        const val LIVE_CHANNEL = "live_limits"
        const val LIVE_ID = 0x4C495645
        const val CHANNEL = "limit_reminders"
        const val KEY_WARNED_DATE = "warned_date"
        const val KEY_WARNED_APPS = "warned_apps"
    }
}

private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)

/** Shows the latest summary the monitor stored; it has no data source of its own. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    companion object {
        private const val PREFS = "today_widget"

        internal fun store(context: Context, summary: TodaySummary, localized: Context) {
            val text = UiText(localized)
            val detail = when {
                summary.tightestApp == null -> localized.getString(R.string.widget_empty)
                summary.tightestRemainingMillis == 0L -> localized.getString(R.string.widget_reached, summary.tightestApp)
                else -> localized.getString(R.string.widget_tightest, summary.tightestApp,
                    formatDuration(summary.tightestRemainingMillis!! / 1000, text))
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("label", localized.getString(R.string.widget_label))
                .putString("used", formatDuration(summary.usedMillis / 1000, text))
                .putString("detail", detail).apply()
            val manager = AppWidgetManager.getInstance(context)
            render(context, manager, manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java)))
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_label, saved.getString("label", null) ?: context.getString(R.string.widget_label))
                setTextViewText(R.id.widget_used, saved.getString("used", null) ?: "—")
                setTextViewText(R.id.widget_detail, saved.getString("detail", null) ?: context.getString(R.string.widget_empty))
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
