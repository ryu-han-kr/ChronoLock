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
import com.chronolock.domain.limitWarning
import com.chronolock.domain.remainingDailyMillis
import com.chronolock.domain.todaySummary
import com.chronolock.feature.UiText
import com.chronolock.feature.formatDuration

/**
 * Glanceable outputs of the monitor: a once-a-day "limit nearly used" notification and the home-screen widget.
 * [localized] must already carry the app's chosen language.
 */
internal class LimitSurfaces(private val context: Context) {
    private val warned = mutableSetOf<DailyKey>()
    private var lastSummary: TodaySummary? = null

    fun update(state: MonitorState, rules: Map<String, AppRule>, localized: Context) {
        state.clock?.date?.let { today -> warned.removeAll { it.date != today } }
        state.limitWarning(rules, warned)?.let { key ->
            warned += key
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

    private companion object { const val CHANNEL = "limit_reminders" }
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
