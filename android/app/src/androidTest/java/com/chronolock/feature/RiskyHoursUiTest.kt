package com.chronolock.feature

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.data.HistoryDailyEntity
import com.chronolock.domain.AppRule
import com.chronolock.domain.RiskyWindow
import com.chronolock.domain.ScheduleDraft
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** History names the busiest hours, and blocking them opens the editor with that daily window already set. */
class RiskyHoursUiTest {
    @get:Rule val ui = createComposeRule()
    private val today = LocalDate.of(2026, 10, 30)

    @Test fun busiestHours_areOffered_withTheirSchedule() {
        var offered: Pair<String, ScheduleDraft>? = null
        val state = MainState(riskyWindow = RiskyWindow("com.video", 23, 2, 5 * 60 * 60_000L),
            history = listOf(HistoryDailyEntity("com.video", today.toString(), "Video", 3_600_000, 4)))
        ui.setContent { LocalizedApp(AppLanguage.KOREAN) { ChronoTheme(AppTheme.DARK) {
            // The card sits in History's scrolling list.
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                WeeklyInsightsCard(state, today, blockHours = { app, window -> offered = app to window }) { _, _ -> }
            }
        } } }
        ui.onNodeWithText("매일 23시~01시", substring = true).assertExists()
        ui.onNodeWithText("Video", substring = true).assertExists()
        ui.onNodeWithTag("risky-block").performScrollTo().performClick()
        ui.runOnIdle { assertEquals("com.video" to ScheduleDraft("23:00", "01:00", ScheduleDraft.EVERY_DAY), offered) }
    }

    @Test fun editor_startsWithTheWindow_andNoDailyLimitForANewRule() {
        var saved: AppRule? = null
        ui.setContent { LocalizedApp(AppLanguage.KOREAN) { ChronoTheme(AppTheme.DARK) {
            RuleEditor(InstalledApp("com.video", "Video"), null, {}, { rule, finished -> saved = rule; finished() }, {},
                initialSchedule = ScheduleDraft("23:00", "01:00", ScheduleDraft.EVERY_DAY))
        } } }
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle {
            val rule = requireNotNull(saved)
            assertNull(rule.dailyLimitSeconds)
            assertEquals(listOf(LocalTime.of(23, 0) to LocalTime.of(1, 0)), rule.schedules.map { it.start to it.end })
        }
    }
}
