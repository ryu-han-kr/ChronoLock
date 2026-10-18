package com.chronolock.feature

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.data.HistoryDailyEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class HistoryUiTest {
    @get:Rule val ui = createComposeRule()
    private val today = LocalDate.of(2026, 10, 15)
    private val state = MainState(permissions = PermissionState(usage = true), loading = false,
        history = listOf(
            HistoryDailyEntity("app.a", today.toString(), "Example A", 3_600_000, 2),
            HistoryDailyEntity("app.b", today.minusDays(3).toString(), "Example B", 3_600_000, 3),
            HistoryDailyEntity("app.c", today.minusDays(20).toString(), "Example C", 3_600_000, 4),
            HistoryDailyEntity("app.d", today.minusDays(30).toString(), "Too old", 3_600_000, 8)))
    @Test fun periodSwitch_changesTotals_andSurvivesRestoration() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent { ChronoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HistoryPanel(state, today, {}, {})
        } } }
        ui.onNodeWithText("1시간 0분", substring = false).assertExists()
        ui.onNodeWithText("일주일").performClick()
        ui.onNodeWithText("2시간 0분", substring = false).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("일주일").assertIsSelected()
        ui.onNodeWithText("한 달").performClick()
        ui.onNodeWithText("3시간 0분", substring = false).assertExists()
        ui.onNodeWithText("9", substring = false).assertExists()
        ui.onNodeWithText("Too old").assertDoesNotExist()
    }
    @Test fun historyOnly_needsUsagePermission_andNoAccessibilityPermission() {
        var requested = false
        ui.setContent { ChronoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HistoryPanel(MainState(), today, {}, { requested = true })
        } } }
        ui.onNodeWithText("사용 정보 권한", substring = false).performClick()
        ui.runOnIdle { assertTrue(requested) }
        ui.onNodeWithText("접근성 권한", substring = false).assertDoesNotExist()
    }
    @Test fun languagePicker_changesExistingUi_withoutLosingSelection() {
        val language = mutableStateOf(AppLanguage.KOREAN)
        ui.setContent { LocalizedApp(language.value) { ChronoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            LanguagePicker(language.value) { language.value = it }
            Text(tr("히스토리"))
            Text(duration(3661))
        } } } }
        val expected = listOf(AppLanguage.ENGLISH to "History", AppLanguage.JAPANESE to "履歴",
            AppLanguage.CHINESE to "历史", AppLanguage.SPANISH to "Historial", AppLanguage.KOREAN to "히스토리")
        expected.forEach { (choice, title) ->
            ui.onNodeWithText(choice.nativeName).performScrollTo().performClick()
            ui.onNodeWithText(title).assertExists()
            ui.onNodeWithText(choice.nativeName).assertIsSelected()
        }
    }
    @Test fun bottomNavigation_opensHistory_withoutAnyRules() {
        val tab = mutableStateOf(0)
        ui.setContent { ChronoTheme { ChronoScreen(state, today, 0, tab.value, { tab.value = it },
            SnackbarHostState(), { _, _ -> }, {}, {}, {}, {}, {}) } }
        ui.onNodeWithText("히스토리").performClick()
        ui.onNodeWithText("하루").assertExists()
        ui.onNodeWithText("총 사용 시간").assertExists()
        ui.onNodeWithText("1시간 0분", substring = false).assertExists()
    }
    @Test fun historyScreenshots_allFiveLanguages() {
        val language = mutableStateOf(AppLanguage.KOREAN)
        ui.setContent { LocalizedApp(language.value) { ChronoTheme(AppTheme.LAVENDER) {
            Surface(Modifier.fillMaxSize()) { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr("히스토리"), style = MaterialTheme.typography.headlineLarge)
                HistoryPanel(state, today, {}, {})
            } }
        } } }
        AppLanguage.values().filter { it != AppLanguage.SYSTEM }.forEach { choice ->
            ui.runOnIdle { language.value = choice }
            ui.waitForIdle()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.getExternalFilesDir(null), "screenshots/history-${choice.tag}.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
