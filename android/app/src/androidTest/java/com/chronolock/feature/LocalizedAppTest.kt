package com.chronolock.feature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.chronolock.data.AppLanguage
import org.junit.Rule
import org.junit.Test

class LocalizedAppTest {
    @get:Rule val ui = createComposeRule()

    // 0.5.0 crashed on the main screen: the notification permission launcher could not find the Activity behind the localized context.
    @Test fun activityResultLauncher_worksInsideLocalizedApp() {
        ui.setContent {
            LocalizedApp(AppLanguage.ENGLISH) {
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                Text("launcher ready")
            }
        }
        ui.onNodeWithText("launcher ready").assertIsDisplayed()
    }
}
