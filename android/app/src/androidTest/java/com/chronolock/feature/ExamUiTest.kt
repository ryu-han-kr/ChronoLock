package com.chronolock.feature

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.domain.Exam
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Today counts down to the exam, and only the last two weeks suggest lowering limits. */
class ExamUiTest {
    @get:Rule val ui = createComposeRule()
    private val exam = Exam(LocalDate.of(2026, 11, 19), "수능")

    @Test fun farOff_showsDaysLeftOnly() {
        ui.setContent { LocalizedApp(AppLanguage.KOREAN) { ChronoTheme(AppTheme.DARK) { ExamCard(exam, LocalDate.of(2026, 10, 30)) {} } } }
        ui.onNodeWithTag("exam-dday").assertTextEquals("D-20")
        ui.onNodeWithText("수능까지").assertExists()
        ui.onNodeWithTag("exam-limits").assertDoesNotExist()
    }

    @Test fun lastTwoWeeks_offerTheLimitsTab() {
        var opened = false
        ui.setContent { LocalizedApp(AppLanguage.KOREAN) { ChronoTheme(AppTheme.DARK) { ExamCard(exam, LocalDate.of(2026, 11, 12)) { opened = true } } } }
        ui.onNodeWithTag("exam-dday").assertTextEquals("D-7")
        ui.onNodeWithTag("exam-limits").performClick()
        ui.runOnIdle { assertTrue(opened) }
    }
}
