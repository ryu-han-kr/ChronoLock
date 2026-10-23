package com.chronolock.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chronolock.data.AppTheme
import com.chronolock.data.ThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun followingDarkMode_showsDayAndNightChoices() {
        val follow = mutableStateOf(false)
        val night = mutableStateOf(false)
        ui.setContent { ChronoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ThemePicker(AppTheme.LIGHT, {}, followSystem = follow.value, editingNight = night.value,
                setFollowSystem = { follow.value = it }, editNight = { night.value = it })
        } } }
        ui.onAllNodesWithTag("theme-night").assertCountEquals(0)
        ui.onNodeWithTag("theme-follow").performClick()
        ui.onNodeWithTag("theme-night").performClick()
        ui.runOnIdle { assertTrue(follow.value && night.value) }
        ui.onNodeWithTag("theme-night").assertIsSelected()
    }

    @Test fun gallery_jumpsToAFamily_andPicksARandomTheme() {
        val selected = mutableStateOf(AppTheme.DARK)
        ui.setContent { ChronoTheme(selected.value) { ThemeGallery(selected.value, { selected.value = it }) {} } }
        ui.onNodeWithTag("theme-jump-${ThemeCatalog.NEUTRAL}").performScrollTo().performClick()
        ui.onNodeWithTag("theme-${ThemeCatalog.neutral.first().name}").assertIsDisplayed()
        ui.onNodeWithTag("theme-random").performClick()
        ui.runOnIdle { assertNotEquals(AppTheme.DARK, selected.value) }
    }
}
