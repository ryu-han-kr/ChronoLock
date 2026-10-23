package com.chronolock.feature

import com.chronolock.data.AppTheme
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun everyTheme_keepsTextReadableOnBothBackgrounds() {
        AppTheme.values().forEach { theme ->
            listOfNotNull(theme.background, theme.surface, theme.gradientEnd).forEach { background ->
                listOf(theme.text, theme.muted, theme.primary).forEach { foreground ->
                    assertTrue("${theme.name}: insufficient text contrast", contrast(foreground, background) >= 4.5)
                }
            }
            assertTrue("${theme.name}: insufficient button contrast", contrast(theme.primary, theme.onPrimary) >= 4.5)
        }
    }
    @Test fun catalogIds_areUniqueAndRoundTrip() {
        val names = AppTheme.values().map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(AppTheme.values().size > 50)
        AppTheme.values().forEach { assertEquals(it, AppTheme.fromStored(it.name)) }
    }
    @Test fun themeTitles_areDistinctAndEveryWordIsTranslated() {
        val titles = AppTheme.values().map { it.title }
        assertEquals(titles.filter { title -> titles.count { it == title } > 1 }.distinct().toString(), titles.size, titles.toSet().size)
        AppTheme.values().flatMap { it.label }.filterNot { it.all(Char::isDigit) }.distinct().forEach { word ->
            assertTrue("Missing translation for theme word: $word", word in translationKeys)
        }
    }
    @Test fun trendNames_startWithThePageColour() {
        // Seed 14 is a mint page with pink buttons; it used to be called "pink 2".
        assertEquals(listOf("트렌드", "민트", "핑크"), com.chronolock.data.ThemeCatalog.trend[13].label)
        assertEquals(listOf("트렌드", "아이보리", "앰버"), com.chronolock.data.ThemeCatalog.trend[12].label)
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
