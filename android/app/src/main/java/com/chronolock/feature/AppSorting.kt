package com.chronolock.feature

import com.chronolock.blocking.InstalledApp
import com.chronolock.data.HistoryDailyEntity
import java.time.LocalDate

/** Order of the app list on the limits tab. */
enum class AppSort { NAME, USAGE, RECENT }

/** Usage of one app over the summarised window: total time, opens and the last day it was seen. */
data class AppUsageSummary(val usageMillis: Long, val opens: Int, val lastDate: String)

/** Rolls daily history rows from [from] onward into one summary per package. */
internal fun summarizeUsage(history: List<HistoryDailyEntity>, from: LocalDate): Map<String, AppUsageSummary> {
    val start = from.toString()
    return history.filter { it.date >= start }.groupBy { it.packageName }.mapValues { (_, rows) ->
        AppUsageSummary(rows.sumOf { it.usageMillis }, rows.sumOf { it.opens }, rows.maxOf { it.date })
    }
}

/** Apps without any usage keep their name order and go after the ones with records. */
internal fun sortApps(apps: List<InstalledApp>, sort: AppSort, usage: Map<String, AppUsageSummary>): List<InstalledApp> = when (sort) {
    AppSort.NAME -> apps.sortedBy { it.label.lowercase() }
    AppSort.USAGE -> apps.sortedWith(compareByDescending<InstalledApp> { usage[it.packageName]?.usageMillis ?: -1L }.thenBy { it.label.lowercase() })
    AppSort.RECENT -> apps.sortedWith(compareByDescending<InstalledApp> { usage[it.packageName]?.lastDate ?: "" }
        .thenByDescending { usage[it.packageName]?.usageMillis ?: -1L }.thenBy { it.label.lowercase() })
}
