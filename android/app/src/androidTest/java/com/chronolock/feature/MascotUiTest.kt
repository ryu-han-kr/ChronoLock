package com.chronolock.feature

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.data.AppTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Renders every hamster mood on a light and a dark theme and saves contact sheets for review. */
class MascotUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun everyMood_rendersOnLightAndDark() {
        ui.setContent {
            Column {
                listOf(AppTheme.LIGHT, AppTheme.DARK).forEach { theme ->
                    ChronoTheme(theme) {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp).testTag("sheet-${theme.name}"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Mood.values().forEach { mood ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Mascot(mood, size = 64.dp)
                                        Text(mood.name.lowercase(), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Mood.values().forEach { mood -> Text(tr(mood.message), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
        Mood.values().forEach { mood -> ui.onNodeWithTag("mascot-${mood.name.lowercase()}", useUnmergedTree = true) }
        screenshot("hamster-light", ui.onNodeWithTag("sheet-LIGHT"))
        screenshot("hamster-dark", ui.onNodeWithTag("sheet-DARK"))
    }

    private fun screenshot(name: String, node: SemanticsNodeInteraction) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
