package com.chronolock.feature

import com.chronolock.data.AppTheme
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun everyTheme_keepsTextReadableOnBothBackgrounds() {
        AppTheme.values().forEach { theme ->
            listOf(theme.background, theme.surface).forEach { background ->
                listOf(theme.text, theme.muted, theme.primary).forEach { foreground ->
                    assertTrue("${theme.name}: insufficient text contrast", contrast(foreground, background) >= 4.5)
                }
            }
            assertTrue("${theme.name}: insufficient button contrast", contrast(theme.primary, theme.onPrimary) >= 4.5)
        }
    }
    @Test fun missingOrUnknownPreference_fallsBackToDark() {
        assertEquals(AppTheme.DARK, AppTheme.fromStored(null))
        assertEquals(AppTheme.DARK, AppTheme.fromStored("removed-theme"))
    }
    private fun contrast(first: Long, second: Long): Double {
        fun luminance(color: Long): Double {
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 255) / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
