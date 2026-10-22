package com.chronolock.feature

import com.chronolock.blocking.InstalledApp
import com.chronolock.data.HistoryDailyEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSortingTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val apps = listOf(InstalledApp("c.chat", "Chat"), InstalledApp("a.video", "Video"), InstalledApp("b.news", "News"), InstalledApp("d.quiet", "Quiet"))
    private val history = listOf(
        HistoryDailyEntity("a.video", "2026-10-20", "Video", 3_600_000, 4),
        HistoryDailyEntity("a.video", "2026-10-21", "Video", 1_800_000, 2),
        HistoryDailyEntity("b.news", "2026-10-22", "News", 600_000, 9),
        HistoryDailyEntity("c.chat", "2026-10-10", "Chat", 9_000_000, 30), // outside the 7-day window
    )
    private val usage = summarizeUsage(history, today.minusDays(6))

    @Test fun summary_sumsWithinWindow_andKeepsLastDay() {
        assertEquals(AppUsageSummary(5_400_000, 6, "2026-10-21"), usage["a.video"])
        assertEquals(null, usage["c.chat"])
    }
    @Test fun byName_isAlphabetical() =
        assertEquals(listOf("Chat", "News", "Quiet", "Video"), sortApps(apps, AppSort.NAME, usage).map { it.label })
    @Test fun byUsage_putsHeaviestFirst_andUnusedLast() =
        assertEquals(listOf("Video", "News", "Chat", "Quiet"), sortApps(apps, AppSort.USAGE, usage).map { it.label })
    @Test fun byRecent_putsLatestDayFirst() =
        assertEquals(listOf("News", "Video", "Chat", "Quiet"), sortApps(apps, AppSort.RECENT, usage).map { it.label })
}
