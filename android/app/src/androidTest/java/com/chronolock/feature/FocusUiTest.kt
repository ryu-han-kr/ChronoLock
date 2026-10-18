package com.chronolock.feature

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.blocking.Health
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.RuleEntity
import com.chronolock.data.AppTheme
import com.chronolock.domain.AppRule
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FocusUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dailyTimeFields_surviveRestorationAndSaveExactSeconds() {
        var saved: AppRule? = null
        val restoration = StateRestorationTester(ui)
        restoration.setContent { ChronoTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), null, {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithTag("daily-hours").performScrollTo().performTextReplacement("1")
        ui.onNodeWithTag("daily-minutes").performScrollTo().performTextReplacement("30")
        ui.onNodeWithTag("daily-seconds").performScrollTo().performTextReplacement("15")
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle { assertEquals(5415L, saved?.dailyLimitSeconds) }
    }

    @Test fun existingDailyLimit_isSplitWithoutLosingSeconds() {
        ui.setContent { ChronoTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), RuleEntity("test.app", "테스트 앱", 5415, null, null),
                {}, { _, finished -> finished() }, {})
        } }
        ui.onNodeWithTag("daily-hours").performScrollTo().assertTextContains("1")
        ui.onNodeWithTag("daily-minutes").assertTextContains("30")
        ui.onNodeWithTag("daily-seconds").assertTextContains("15")
        screenshot("daily-time-fields", ui.onNode(isDialog()))
    }

    @Test fun sessionAndCooldown_useTimeFieldsAndPresets() {
        var saved: AppRule? = null
        ui.setContent { ChronoTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), RuleEntity("test.app", "테스트 앱", null, null, 90, sessionLimitSeconds = 3725),
                {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithTag("session-hours").performScrollTo().assertTextContains("1")
        ui.onNodeWithTag("session-minutes").assertTextContains("2")
        ui.onNodeWithTag("session-seconds").assertTextContains("5")
        ui.onNodeWithTag("cooldown-minutes").performScrollTo().assertTextContains("1")
        ui.onNodeWithTag("cooldown-seconds").assertTextContains("30")
        ui.onNodeWithTag("session-preset-10").performScrollTo().performClick().assertIsSelected()
        ui.onNodeWithTag("session-minutes").assertTextContains("10")
        ui.onNodeWithTag("cooldown-seconds").performScrollTo().performTextReplacement("45")
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle {
            assertEquals(600L, saved?.sessionLimitSeconds)
            assertEquals(105L, saved?.cooldownSeconds)
        }
    }

    @Test fun invalidCooldownPart_namesTheCooldownField() {
        var saved: AppRule? = null
        ui.setContent { ChronoTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), null, {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithTag("cooldown-seconds").performScrollTo().performTextReplacement("60")
        ui.onNodeWithText("저장").performClick()
        ui.onNodeWithText("재실행 대기 시간: 초 항목에 0~59의 정수를 입력해 주세요.").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { assertNull(saved) }
    }

    @Test fun newSchedule_requiresExplicitDaySelection() {
        var saved: AppRule? = null
        ui.setContent { ChronoTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), null, {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithText("요일·시간 예약").performScrollTo().performClick()
        listOf("월", "화", "수", "목", "금", "토", "일").forEach {
            ui.onNodeWithText(it).performScrollTo().assertIsNotSelected()
        }
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle { assertNull(saved) }
        ui.onNodeWithText("예약할 요일을 하나 이상 선택해 주세요.").assertExists()
        ui.onNodeWithText("수").performScrollTo().performClick()
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle { assertEquals(setOf(java.time.DayOfWeek.WEDNESDAY), saved?.schedule?.days) }
    }

    @Test fun themePicker_switchesAllFiveThemes() {
        val selected = mutableStateOf(AppTheme.DARK)
        ui.setContent { ChronoTheme(selected.value) {
            Surface(Modifier.fillMaxSize()) { ThemePicker(selected.value) { selected.value = it } }
        } }
        AppTheme.values().forEach { theme ->
            ui.onNodeWithText(theme.title).performClick().assertIsSelected()
            ui.runOnIdle { assertEquals(theme, selected.value) }
            screenshot("theme-${theme.name.lowercase()}", ui.onRoot())
        }
    }

    @Test fun recipeIsPreviewed_thenSavedWithSessionAndCooldown() {
        var saved: AppRule? = null
        ui.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), null, {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithText("짧게 보고 쉬기").performClick()
        ui.runOnIdle { assertNull(saved) }
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle {
            assertEquals(300L, saved?.sessionLimitSeconds)
            assertEquals(600L, saved?.cooldownSeconds)
            assertNull(saved?.dailyLimitSeconds)
        }
        screenshot("rule-editor", ui.onNode(isDialog()))
    }

    @Test fun invalidScheduleCannotBeSaved() {
        var saved: AppRule? = null
        ui.setContent { MaterialTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), null, {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithText("취침 시간 지키기").performScrollTo().performClick()
        ui.onNodeWithText("차단 시작 (HH:mm)").performScrollTo().performTextReplacement("24:00")
        ui.onNodeWithText("저장").performClick()
        ui.onNodeWithText("예약 시각은 23:00처럼 00:00~23:59로 입력해 주세요.").assertExists()
        ui.runOnIdle { assertNull(saved) }
    }

    @Test fun sleepRecipeSavesOvernightScheduleWithoutDailyLimit() {
        var saved: AppRule? = null
        ui.setContent { MaterialTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), null, {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithText("취침 시간 지키기").performScrollTo().performClick()
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle {
            assertEquals("23:00", saved?.schedule?.start.toString())
            assertEquals("07:00", saved?.schedule?.end.toString())
            assertEquals(7, saved?.schedule?.days?.size)
            assertNull(saved?.dailyLimitSeconds)
        }
    }

    @Test fun quickFocusRequiresTargetsAndPermissions() {
        ui.setContent { MaterialTheme { QuickFocusCard(MainState(), 1000, { fail("Should not start") }, {}) } }
        ui.onNodeWithText("25분").assertIsNotEnabled()
        ui.onNodeWithText("50분").assertIsNotEnabled()
        ui.onNodeWithText("90분").assertIsNotEnabled()
    }

    @Test fun quickFocusStartUsesSelectedDuration() {
        var minutes: Int? = null
        val state = MainState(rules = listOf(RuleEntity("test.app", "YouTube", 600, null, null)),
            permissions = PermissionState(true, true), health = Health(connected = true))
        ui.setContent { MaterialTheme(colorScheme = darkColorScheme()) { QuickFocusCard(state, 1000, { minutes = it }, {}) } }
        ui.onNodeWithText("25분").performClick()
        ui.runOnIdle { assertEquals(25, minutes) }
        screenshot("quick-focus-ready", ui.onRoot())
    }

    @Test fun runningFocusShowsCountdownAndCanBeStopped() {
        var stopped = false
        val state = MainState(rules = listOf(RuleEntity("test.app", "YouTube", 600, null, null, focusUntilEpochMillis = 301000)),
            permissions = PermissionState(true, true), health = Health(connected = true))
        ui.setContent { MaterialTheme(colorScheme = darkColorScheme()) { QuickFocusCard(state, 1000, {}, { stopped = true }) } }
        ui.onNodeWithText("5분 0초").assertExists()
        ui.onNodeWithText("집중 종료").performClick()
        ui.runOnIdle { assertTrue(stopped) }
        screenshot("quick-focus-running", ui.onRoot())
    }

    @Test fun editorDraft_survivesSavedStateRestoration() {
        var saved: AppRule? = null
        val restoration = StateRestorationTester(ui)
        restoration.setContent { MaterialTheme {
            RuleEditor(InstalledApp("test.app", "테스트 앱"), null, {}, { rule, finished -> saved = rule; finished() }, {})
        } }
        ui.onNodeWithText("짧게 보고 쉬기").performClick()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("저장").performClick()
        ui.runOnIdle {
            assertEquals(300L, saved?.sessionLimitSeconds)
            assertEquals(600L, saved?.cooldownSeconds)
        }
    }

    private fun screenshot(name: String, node: SemanticsNodeInteraction) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
