package com.chronolock.feature

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.domain.StarterLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The first-week card lists each suggested limit and applies them all, or goes away. */
class StarterCardTest {
    @get:Rule val ui = createComposeRule()
    private val limits = listOf(StarterLimit("com.video", 130 * 60_000L, 90 * 60L), StarterLimit("com.chat", 60 * 60_000L, 40 * 60L))
    private val state = MainState(apps = listOf(InstalledApp("com.video", "Video"), InstalledApp("com.chat", "Chat")))

    @Test fun applyAll_sendsEverySuggestion() {
        var applied: List<StarterLimit>? = null
        ui.setContent { LocalizedApp(AppLanguage.KOREAN) { ChronoTheme(AppTheme.DARK) { StarterCard(state, limits, { applied = it }) {} } } }
        ui.onNodeWithText("Video ·", substring = true).assertExists()
        ui.onNodeWithText("Chat ·", substring = true).assertExists()
        ui.onNodeWithTag("starter-apply").performClick()
        ui.runOnIdle { assertEquals(limits, applied) }
    }

    @Test fun noThanks_dismisses() {
        var dismissed = false
        ui.setContent { LocalizedApp(AppLanguage.KOREAN) { ChronoTheme(AppTheme.DARK) { StarterCard(state, limits, {}) { dismissed = true } } } }
        ui.onNodeWithTag("starter-dismiss").performClick()
        ui.runOnIdle { assertTrue(dismissed) }
    }
}
