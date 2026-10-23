package com.chronolock.feature

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
        ui.setContent { ChronoTheme { GroupEditor(null, apps, 0, onDismiss = {}, onSave = { _, _ -> }, onDelete = {}) } }
        ui.onNodeWithTag("group-app-app.1").performScrollTo().performClick()
        ui.onNodeWithTag("group-app-app.2").performScrollTo().performClick()
        ui.onNodeWithTag("group-save").performClick()
        ui.onNodeWithTag("group-error").assertIsDisplayed().assertTextEquals("그룹 이름을 입력해 주세요.")
        ui.onNodeWithTag("group-name").assertIsDisplayed()
    }

    @Test fun selectAll_warnsFirst_thenSelectsEverySearchResult() {
        ui.setContent { ChronoTheme { GroupEditor(null, apps, 0, onDismiss = {}, onSave = { _, _ -> }, onDelete = {}) } }
        ui.onNodeWithTag("group-select-all").performScrollTo().performClick()
        ui.onNodeWithText("검색된 앱을 모두 선택할까요?").assertIsDisplayed()
        ui.onNodeWithTag("group-select-all-confirm").performClick()
        ui.onNodeWithText("묶을 앱 · 80개 선택").assertExists()
        ui.onNodeWithTag("group-select-none").performClick()
        ui.onNodeWithText("묶을 앱 · 0개 선택").assertExists()
    }
}
