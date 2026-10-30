package com.chronolock.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronolock.data.AppTheme
import com.chronolock.data.MascotKind
import com.chronolock.data.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The week card renders at story size in every theme, and its picture is not left blank. */
@RunWith(AndroidJUnit4::class)
class ShareCardTest {
    @Test fun card_isStorySized_andDrawn() {
        val context: Context = ApplicationProvider.getApplicationContext()
        AppTheme.values().forEach { theme ->
            val card = ShareCard.render(context, theme, MascotKind.DEFAULT, Pose.SUCCESS, "이번 주 ChronoLock",
                "지난주보다 23% 덜 썼어요", listOf("이번 주 사용 3시간 20분"), "ChronoLock · 앱 사용시간 제한")
            assertEquals(1080 to 1920, card.width to card.height)
            // The character sits in the upper middle; the page background alone would match the corner.
            assertNotEquals(theme.name, card.getPixel(0, 0), card.getPixel(540, 520))
            // Kept for a look by eye, like the other UI tests' screenshots.
            if (theme == AppTheme.DARK) java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                .resolve("share-card.png").outputStream().use { card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
