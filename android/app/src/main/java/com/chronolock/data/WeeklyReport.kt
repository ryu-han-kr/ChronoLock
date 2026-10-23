package com.chronolock.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.chronolock.MainActivity
import com.chronolock.R
import com.chronolock.domain.weekTotals
import com.chronolock.domain.weeklyReportDue
import com.chronolock.feature.UiText
import com.chronolock.feature.formatDuration
import com.chronolock.feature.forLanguage
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

/** Sunday-evening summary of the week against the one before, sent at most once a week by the history job. */
internal object WeeklyReport {
    private const val CHANNEL = "weekly_report"
    private const val ID = 0x5745454B
    private const val KEY = "weekly_report_week"

    suspend fun maybeNotify(context: Context, history: HistoryRepository, preferences: Preferences, now: LocalDateTime = LocalDateTime.now()) {
        val prefs = context.getSharedPreferences("weekly_report", Context.MODE_PRIVATE)
        val last = prefs.getString(KEY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val week = weeklyReportDue(now, last) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val totals = weekTotals(history.year.first().associate { LocalDate.parse(it.date) to it.usageMillis }, week.plusDays(6))
        prefs.edit { putString(KEY, week.toString()) }
        if (totals.thisWeekMillis == 0L) return
        val localized = context.forLanguage(preferences.language.first())
        val text = UiText(localized)
        val used = formatDuration(totals.thisWeekMillis / 1000, text)
        val body = when (val change = totals.changePercent) {
            null -> localized.getString(R.string.weekly_report_first, used)
            else -> if (change <= 0) localized.getString(R.string.weekly_report_less, used, -change)
                else localized.getString(R.string.weekly_report_more, used, change)
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.weekly_report_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            manager.notify(ID, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_chronolock)
                .setContentTitle(localized.getString(R.string.weekly_report_title)).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
