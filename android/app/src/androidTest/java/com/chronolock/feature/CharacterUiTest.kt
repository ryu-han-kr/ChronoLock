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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.chronolock.data.AppTheme
import com.chronolock.data.MascotKind
import com.chronolock.data.Pose
import java.io.File
import org.junit.Rule
import org.junit.Test

/** The character picker, the Today moods and every pose of every character; saves sheets for review. */
class CharacterUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun picker_switchesTheCharacterEverywhere() {
        val chosen = mutableStateOf(MascotKind.DEFAULT)
        ui.setContent {
            CompositionLocalProvider(LocalMascot provides chosen.value) {
                ChronoTheme(AppTheme.LIGHT) {
                    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("picker-sheet"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MascotPicker(chosen.value, select = { chosen.value = it })
                        listOf(
                            todayMood(locked = false, focusing = false, limitReached = false, nearLimit = false, paused = false, hour = 14, hasRules = true),
                            todayMood(locked = false, focusing = false, limitReached = false, nearLimit = true, paused = false, hour = 14, hasRules = true),
                            todayMood(locked = false, focusing = false, limitReached = false, nearLimit = false, paused = false, hour = 23, hasRules = true),
                        ).forEach { mood ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MascotArt(mood.pose, size = 72.dp)
                                Text(tr(mood.line))
                            }
                        }
                    }
                }
            }
        }
        ui.onNodeWithTag("mascot-pick-otter").performClick()
        ui.onNodeWithTag("mascot-pick-otter").assertIsSelected()
        ui.runOnIdle { org.junit.Assert.assertEquals(MascotKind.OTTER, chosen.value) }
        screenshot("character-picker", ui.onNodeWithTag("picker-sheet"))
    }

    @Test fun reduceMotion_switchFlips_andStillArtStillRenders() {
        val still = mutableStateOf(false)
        ui.setContent {
            CompositionLocalProvider(LocalReduceMotion provides still.value) {
                ChronoTheme(AppTheme.LIGHT) { Column { MotionToggle(still.value) { still.value = it }; MascotArt(Pose.REST, Modifier.testTag("still-art")) } }
            }
        }
        ui.onNodeWithTag("reduce-motion").performClick()
        ui.runOnIdle { org.junit.Assert.assertTrue(still.value) }
        ui.onNodeWithTag("still-art").assertExists()
    }

    @Test fun freeVersion_keepsTheHamster_andMarksTheOthersPro() {
        val chosen = mutableStateOf(MascotKind.HAMSTER)
        ui.setContent { ChronoTheme(AppTheme.LIGHT) { MascotPicker(chosen.value, { chosen.value = it }, pro = false) } }
        ui.onNodeWithTag("mascot-locked-otter").assertExists()
        ui.onAllNodesWithTag("mascot-locked-hamster").assertCountEquals(0)
        ui.onNodeWithTag("mascot-pick-otter").performClick()
        ui.runOnIdle { org.junit.Assert.assertEquals(MascotKind.HAMSTER, chosen.value) }
        ui.onNodeWithText("햄스터 외의 캐릭터는 프로에서 쓸 수 있어요.").assertExists()
    }

    @Test fun everyCharacter_hasEveryPose() {
        ui.setContent {
            ChronoTheme(AppTheme.DARK) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp).testTag("pose-sheet")) {
                    MascotKind.entries.forEach { kind ->
                        Row { Pose.entries.forEach { pose -> MascotArt(pose, size = 36.dp, kind = kind) } }
                    }
                }
            }
        }
        screenshot("character-poses", ui.onNodeWithTag("pose-sheet"))
    }

    private fun screenshot(name: String, node: SemanticsNodeInteraction) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
