package com.chronolock.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.domain.DeskQr
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The printed code reads back as this device's code, and the switch holds while a focus runs. */
class DeskQrUiTest {
    @get:Rule val ui = createComposeRule()
    private val secret = DeskQr.newSecret(ByteArray(16) { (it * 7).toByte() })

    @Test fun drawnCode_readsBackAsThisDevicesCode() {
        val bitmap = DeskQrCode.bitmap(secret)
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val text = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
        assertEquals(DeskQr.payload(secret), text)
        assertTrue(DeskQr.matches(secret, text))
    }

    @Test fun card_showsTheCode_andCannotBeTurnedOffDuringFocus() {
        var turned: Boolean? = null
        ui.setContent { LocalizedApp(AppLanguage.KOREAN) { ChronoTheme(AppTheme.DARK) {
            Column { DeskQrCard(secret, focusRunning = true) { turned = it } }
        } } }
        ui.onNodeWithTag("desk-qr-image").assertExists()
        ui.onNodeWithText("집중이 끝난 뒤에 바꿀 수 있어요.").assertExists()
        ui.onNodeWithTag("desk-qr").assertIsNotEnabled().performClick()
        ui.runOnIdle { assertEquals(null, turned) }
    }
}
