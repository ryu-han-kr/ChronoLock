package com.chronolock.feature

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.blocking.Health
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.RuleEntity
import com.chronolock.domain.AppRule
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FocusUiTest {
    @get:Rule val ui = createComposeRule()

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
