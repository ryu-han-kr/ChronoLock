package com.chronolock.feature

import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.blocking.Health
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.HistoryDailyEntity
import com.chronolock.data.RuleEntity
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/** Run on phone, tablet and large-font configurations to cover the adaptive screen. */
class ChronoLayoutTest {
    @get:Rule val ui = createComposeRule()
    private val today = LocalDate.of(2026, 10, 16)
    private val state = MainState(
        apps = (0..29).map { InstalledApp("sample.app$it", "Sample App $it") },
        rules = listOf(RuleEntity("sample.app0", "Sample App 0", 3600, null, null)),
        permissions = PermissionState(true, true), health = Health(connected = true), loading = false,
        history = listOf(HistoryDailyEntity("sample.app0", today.toString(), "Sample App 0", 600_000, 3)),
    )

    @Composable
    private fun Screen(initialTab: Int) {
        var tab by rememberSaveable { mutableIntStateOf(initialTab) }
        ChronoTheme {
            ChronoScreen(state, today, 0, tab, { tab = it }, remember { SnackbarHostState() },
                { _, _ -> }, {}, {}, {}, {}, {})
        }
    }

    private fun navigate(title: String) = ui.onNode(hasText(title) and isSelectable()).performClick()
    private fun scrollTo(text: String) = ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))

    @Test fun searchAndConfiguredFilter_surviveTabSwitchAndRestoration() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent { Screen(1) }
        scrollTo("앱 검색")
        ui.onNodeWithText("앱 검색").performTextReplacement("SAMPLE.APP0")
        scrollTo("Sample App 0")
        ui.onNodeWithText("Sample App 0").assertIsDisplayed()
        scrollTo("설정한 앱")
        ui.onNodeWithText("설정한 앱").performClick().assertIsSelected()
        navigate("히스토리")
        navigate("앱 제한")
        restoration.emulateSavedInstanceStateRestore()
        scrollTo("앱 검색")
        ui.onNodeWithText("앱 검색").assertTextContains("SAMPLE.APP0")
        ui.onNodeWithText("설정한 앱").assertIsSelected()
        ui.onNodeWithText("앱 검색").performTextReplacement("missing.app")
        scrollTo("검색 결과가 없습니다.")
        ui.onNodeWithText("검색 결과가 없습니다.").assertIsDisplayed()
        scrollTo("앱 검색")
        ui.onNodeWithText("지우기").performClick()
        scrollTo("Sample App 0")
        ui.onNodeWithText("Sample App 0").assertIsDisplayed()
    }

    @Test fun appListScrollPosition_survivesTabSwitch() {
        ui.setContent { Screen(1) }
        scrollTo("Sample App 25")
        ui.onNodeWithText("Sample App 25").assertIsDisplayed()
        navigate("설정")
        navigate("앱 제한")
        ui.onNodeWithText("Sample App 25").assertIsDisplayed()
    }

    @Test fun allDestinations_areReachable_andCaptured() {
        ui.setContent { Screen(0) }
        listOf("오늘", "앱 제한", "히스토리", "설정").forEachIndexed { index, title ->
            navigate(title)
            ui.onNode(hasText(title) and isSelectable()).assertIsSelected().assertIsDisplayed()
            ui.waitForIdle()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.getExternalFilesDir(null), "screenshots/layout-tab-$index.png")
            file.parentFile?.mkdirs()
            file.outputStream().use {
                ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
