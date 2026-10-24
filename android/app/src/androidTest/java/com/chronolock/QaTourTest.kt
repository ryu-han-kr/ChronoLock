package com.chronolock

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.data.Preferences
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Release QA tour: the real app with this device's own usage records, in every language, saving a screenshot of
 * Today, History (week, month, year) and Settings (character, themes). Opt-in: `-e qaTour true`.
 * Screenshots land in the app's external files dir under qa/.
 */
class QaTourTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun tour_everyLanguage() {
        assumeTrue("Opt-in QA tour", InstrumentationRegistry.getArguments().getString("qaTour") == "true")
        instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS allow").close()
        val preferences = Preferences(context)
        runBlocking { preferences.acknowledge(); preferences.finishOnboarding(); preferences.setTheme(AppTheme.DARK) }
        ActivityScenario.launch(MainActivity::class.java).use {
            AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach { language ->
                runBlocking { preferences.setLanguage(language) }
                ui.waitForIdle()
                val tag = language.tag.lowercase()
                ui.onNodeWithTag("nav-0").performClick(); settle()
                shot("$tag-1-today", ui.onRoot())
                ui.onNodeWithTag("nav-2").performClick(); settle()
                listOf(7, 30, 365).forEach { days ->
                    ui.onNodeWithTag("main-list").performScrollToNode(hasTestTag("history-period-$days"))
                    ui.onNodeWithTag("history-period-$days").performClick(); settle()
                    shot("$tag-2-history-$days", ui.onRoot())
                }
                ui.onNodeWithTag("nav-3").performClick(); settle()
                ui.onNodeWithTag("main-list").performScrollToNode(hasTestTag("mascot-pick-hamster")); settle()
                shot("$tag-3-settings-character", ui.onRoot())
            }
            // A light and a moving theme on Today, in Korean.
            runBlocking { preferences.setLanguage(AppLanguage.KOREAN) }
            listOf(AppTheme.LIGHT, AppTheme.LAVENDER).forEach { theme ->
                runBlocking { preferences.setTheme(theme) }
                ui.onNodeWithTag("nav-0").performClick(); settle()
                shot("ko-4-theme-${theme.name.lowercase()}", ui.onRoot())
            }
            runBlocking { preferences.setTheme(AppTheme.DARK) }
        }
    }

    private fun settle() { ui.waitForIdle(); Thread.sleep(600); ui.waitForIdle() }

    private fun shot(name: String, node: SemanticsNodeInteraction) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
}
