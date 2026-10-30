package com.chronolock.feature

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chronolock.blocking.InstalledApp
import org.junit.Rule
import org.junit.Test

class GroupEditorUiTest {
    @get:Rule val ui = createComposeRule()
    // Many apps, as on a real phone, so the end of the list is far below the name field.
    private val apps = (1..80).map { InstalledApp("app.$it", "App $it") }

    @Test fun savingWithoutName_showsTheErrorAtTheTop_notBelowTheAppList() {
        ui.setContent { ChronoTheme { GroupEditor(null, apps, emptyMap(), 0, onDismiss = {}, onSave = { _, _ -> }, onDelete = {}) } }
        ui.onNodeWithTag("group-app-app.1").performScrollTo().performClick()
        ui.onNodeWithTag("group-app-app.2").performScrollTo().performClick()
        ui.onNodeWithTag("group-save").performClick()
        ui.onNodeWithTag("group-error").assertIsDisplayed().assertTextEquals("그룹 이름을 입력해 주세요.")
        ui.onNodeWithTag("group-name").assertIsDisplayed()
    }

    @Test fun selectAll_warnsFirst_thenSelectsEverySearchResult() {
        ui.setContent { ChronoTheme { GroupEditor(null, apps, emptyMap(), 0, onDismiss = {}, onSave = { _, _ -> }, onDelete = {}) } }
        ui.onNodeWithTag("group-select-all").performScrollTo().performClick()
        ui.onNodeWithText("검색된 앱을 모두 선택할까요?").assertIsDisplayed()
        ui.onNodeWithTag("group-select-all-confirm").performClick()
        ui.onNodeWithText("묶을 앱 · 80개 선택").assertExists()
        ui.onNodeWithTag("group-select-none").performClick()
        ui.onNodeWithText("묶을 앱 · 0개 선택").assertExists()
    }

    @Test fun appsOfAnotherGroup_cannotBeChosen_andBlockHoursCanBeSet() {
        var saved: com.chronolock.domain.AppGroup? = null
        val taken = mapOf("app.1" to "SNS")
        ui.setContent { ChronoTheme { GroupEditor(null, apps, taken, 0, onDismiss = {}, onSave = { group, _ -> saved = group }, onDelete = {}) } }
        ui.onNodeWithText("SNS 그룹에 들어 있음").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("group-app-app.1").assertIsNotEnabled()
        ui.onNodeWithTag("group-select-all").performScrollTo().performClick()
        ui.onNodeWithTag("group-select-all-confirm").performClick()
        ui.onNodeWithText("묶을 앱 · 79개 선택").assertExists()
        ui.onNodeWithTag("group-schedule").performScrollTo().performClick()
        ui.onNodeWithTag("schedule-0-127").performScrollTo().performClick()
        // Typed last: the keyboard it raises would cover the fields below.
        ui.onNodeWithTag("group-name").performScrollTo().performTextInput("밤")
        ui.onNodeWithTag("group-save").performClick()
        ui.runOnIdle {
            org.junit.Assert.assertEquals(1, saved?.schedules?.size)
            org.junit.Assert.assertFalse("app.1" in saved!!.members)
        }
    }

    @Test fun blockedSiteWithItsAppInstalled_offersToLimitTheApp() {
        var limited: String? = null
        ui.setContent { ChronoTheme { SitesCard(listOf(com.chronolock.domain.BlockedSite("youtube.com", true)), emptyList(), { _, _ -> }, { _, _ -> }, {}, {},
            installed = mapOf("com.google.android.youtube" to "YouTube"), limitApp = { limited = it }) } }
        ui.onNodeWithText("YouTube 앱은 사이트 차단으로 막히지 않아요.").assertExists()
        ui.onNodeWithText("앱도 제한하기").performClick()
        ui.runOnIdle { org.junit.Assert.assertEquals("com.google.android.youtube", limited) }
    }

    @Test fun sitePreset_addsItsDomains() {
        var added: List<String>? = null
        ui.setContent { ChronoTheme { SitesCard(emptyList(), emptyList(), { _, _ -> }, { _, _ -> }, {}, {}, addPreset = { added = it }) } }
        ui.onNodeWithTag("site-preset-VIDEO").performClick()
        ui.runOnIdle { org.junit.Assert.assertTrue("youtube.com" in added.orEmpty()) }
    }

    @Test fun lockCard_asksBeforeATimeLock_andShowsPiecesOfAGoalLock() {
        var hours: Int? = null
        val state = androidx.compose.runtime.mutableStateOf(MainState())
        ui.setContent { ChronoTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            LockCard(state.value, java.time.LocalDate.of(2026, 10, 5), 0, keptStreak = 3, lockFor = { hours = it }, startGoal = { _, _, _, _, _, _ -> },
                endGoal = {}, collectPiece = {}, changeGoalPin = { _, _, _, _ -> })
        } } }
        ui.onNodeWithTag("lock-24").performScrollTo().performClick()
        ui.onNodeWithTag("lock-confirm").performClick()
        ui.runOnIdle { org.junit.Assert.assertEquals(24, hours) }
        state.value = MainState(lock = com.chronolock.domain.LockSetting(goalDays = 5, goalStart = java.time.LocalDate.of(2026, 10, 1), pieceCode = true, revealed = "47"))
        ui.onNodeWithTag("lock-pieces").performScrollTo().assertTextEquals("4 7 _ _ _")
        ui.onNodeWithTag("lock-collect").assertIsEnabled()
    }
}
