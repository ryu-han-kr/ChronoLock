package com.chronolock.feature

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.chronolock.data.AppTheme
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun everyTheme_keepsTextReadableOnBothBackgrounds() {
        AppTheme.values().forEach { theme ->
            (theme.pageColors + theme.surface).forEach { background ->
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
    @Test fun liveThemes_flowOverSeveralColours_andRainbowsCoverTheWheel() {
        assertTrue(com.chronolock.data.ThemeCatalog.live.isNotEmpty())
        com.chronolock.data.ThemeCatalog.live.forEach { assertTrue(it.name, it.live && it.pageColors.size >= 2) }
        assertEquals(6, com.chronolock.data.ThemeCatalog.live.first { it.name == "live_rainbow" }.pageColors.size)
        assertFalse(com.chronolock.data.ThemeCatalog.custom(0x4F7CAC, com.chronolock.data.ThemeEffect.SUNSET).live)
        assertTrue(com.chronolock.data.ThemeCatalog.custom(0x4F7CAC, com.chronolock.data.ThemeEffect.FLOW).let { it.live && it.pageColors.size == 2 })
    }
    @Test fun trendNames_startWithThePageColour() {
        val trend = com.chronolock.data.ThemeCatalog.trend.associateBy { it.name }
        // Seed 14 is a mint page with pink buttons; it used to be called "pink 2".
        assertEquals(listOf("트렌드", "민트", "핑크"), trend.getValue("trend_14").label)
        assertEquals(listOf("트렌드", "아이보리", "앰버"), trend.getValue("trend_13").label)
        // Seed 3's button is nudged to a grey-brown, so it is not called coral; seed 9 rebuilt into the same palette.
        assertEquals(listOf("트렌드", "버터"), trend.getValue("trend_3").label)
        assertFalse("trend_9" in trend)
        assertEquals("trend_3", AppTheme.fromStored("trend_9").name)
        // With the repeat gone no trend palette needs a number to tell it apart.
        assertTrue(trend.values.flatMap { it.label }.none { word -> word.all(Char::isDigit) })
    }
    @Test fun hueWordsInNames_matchTheColoursShown() {
        val catalog = com.chronolock.data.ThemeCatalog
        // Page word first, then (for two-colour families) the accent or the gradient's bottom colour.
        val families = catalog.trend + catalog.pastel + catalog.cool + catalog.duotone + catalog.gradient + catalog.dual + catalog.deep +
            catalog.live
        families.forEach { theme ->
            // A moving palette's colour word ("오로라 바이올렛") names where the page starts.
            if (theme in catalog.live) return@forEach theme.label.filter { it in HUE_WORDS }.forEach { assertHue(theme.name, it, theme.background) }
            val words = theme.label.drop(1)
            words.getOrNull(0)?.takeIf { it in HUE_WORDS }?.let { assertHue(theme.name, it, theme.background) }
            words.getOrNull(1)?.takeIf { it in HUE_WORDS }?.let { word ->
                val second = if (theme in catalog.dual) theme.pageColors.last() else theme.primary
                assertHue(theme.name, word, second)
            }
        }
        // A greyish accent is never named by a hue word.
        catalog.trend.filter { it.label.size == 3 }.forEach { assertTrue(it.name, saturationOf(it.primary) >= .2) }
        // Lavender used to end in green with a green button.
        val lavender = catalog.live.first { it.name == "live_lavender" }
        (lavender.pageColors + lavender.primary).forEach { assertTrue("live_lavender %X".format(it), hueOf(it) in 250..330) }
    }
    @Test fun colourSchemeRoles_keepTextReadable() {
        val customs = listOf(0x000000, 0xFFFFFF, 0x808080, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0x4F7CAC, 0x39FF14)
            .flatMap { rgb -> com.chronolock.data.ThemeEffect.entries.map { com.chronolock.data.ThemeCatalog.custom(rgb, it) } +
                com.chronolock.data.ThemeCatalog.custom(rgb, setOf(com.chronolock.data.ThemeEffect.DEEP, com.chronolock.data.ThemeEffect.RAINBOW,
                    com.chronolock.data.ThemeEffect.DUOTONE, com.chronolock.data.ThemeEffect.FLOW)) }
        (AppTheme.values() + customs).forEach { theme ->
            val scheme = theme.colorScheme()
            val pages = theme.pageColors
            // Gradients and flowing pages blend neighbouring colours; text must read on every blend, not only on the stops.
            val blends = if (pages.size == 1) pages else pages.zipWithNext().flatMap { (a, b) -> (0..10).map { blend(a, b, it / 10.0) } }
            (blends + theme.surface).forEach { page ->
                listOf(theme.text, theme.muted, theme.primary).forEach { assertTrue("${theme.name}: %X on blend %X".format(it, page), contrast(it, page) >= 4.5) }
                // Selected chips and the navigation pill: a translucent accent tint over the page or a card.
                val chip = scheme.secondaryContainer.compositeOver(androidx.compose.ui.graphics.Color(page)).toLong()
                assertTrue("${theme.name}: chip label", contrast(scheme.onSecondaryContainer.toLong(), chip) >= 4.5)
            }
            assertTrue("${theme.name}: primary container", contrast(scheme.onPrimaryContainer.toLong(), scheme.primaryContainer.toLong()) >= 4.5)
            // Dialogs, menus and filled fields sit on the raised containers; their copy is muted and their buttons are the accent.
            listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHighest, scheme.surfaceBright).forEach { container ->
                listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary).forEach { text ->
                    assertTrue("${theme.name}: %X on raised %X".format(text.toLong(), container.toLong()), contrast(text.toLong(), container.toLong()) >= 4.5)
                }
            }
        }
    }
    @Test fun storedThemeNames_survive() {
        // Day and night themes, favourites and backups all store names; unknown ones fall back instead of failing.
        listOf("custom_4F7CAC_PASTEL", "custom_000000_SOLID", "custom_FFFFFF_DUOTONE+MIDNIGHT+RAINBOW+FLOW").forEach { name ->
            assertEquals(name, AppTheme.fromStored(name).name)
        }
        listOf("custom_4F7CAC", "custom_4F7CAC_NOPE", "custom_4f7cac_PASTEL", "custom_4F7CAC_PASTEL+", "trend_99", "").forEach { name ->
            assertEquals(name, AppTheme.DARK, AppTheme.fromStored(name))
        }
        // Two effects of one group keep the later one, so a hand-edited name still rebuilds a valid palette.
        assertEquals("custom_4F7CAC_MIST", AppTheme.fromStored("custom_4F7CAC_PASTEL+MIST").name)
    }
    @Test fun customThemes_stayReadableForAnyColourAndEffect_andRoundTrip() {
        val colours = listOf(0x000000, 0xFFFFFF, 0x808080, 0x777777, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF,
            0x4F7CAC, 0x123456, 0xFEDCBA, 0x39FF14, 0x8B4513) + (0 until 40).map { (it * 0x6F1D3B) and 0xFFFFFF }
        // Every single effect and every mix of one page, one gradient, the accent and the motion effect.
        val groups = com.chronolock.data.ThemeEffect.Group.entries.map { group -> listOf(null) + com.chronolock.data.ThemeEffect.entries.filter { it.group == group } }
        val mixes = groups.fold(listOf(emptySet<com.chronolock.data.ThemeEffect>())) { sets, options -> sets.flatMap { set -> options.map { set + listOfNotNull(it) } } }
            .filter { it.isNotEmpty() }
        assertEquals(6 * 6 * 2 * 2 - 1, mixes.size)
        colours.forEach { rgb ->
            mixes.forEach { effects ->
                val theme = com.chronolock.data.ThemeCatalog.custom(rgb, effects)
                (theme.pageColors + theme.surface).forEach { background ->
                    listOf(theme.text, theme.muted, theme.primary).forEach { foreground ->
                        assertTrue("${theme.name}: insufficient contrast %X on %X".format(foreground, background), contrast(foreground, background) >= 4.5)
                    }
                }
                assertTrue("${theme.name}: insufficient button contrast", contrast(theme.primary, theme.onPrimary) >= 4.5)
                assertEquals(theme.background, AppTheme.fromStored(theme.name).background)
                assertEquals(theme.pageColors, AppTheme.fromStored(theme.name).pageColors)
            }
        }
        // Names saved before effects could be combined still rebuild the same palette.
        assertEquals("custom_4F7CAC_SUNSET", com.chronolock.data.ThemeCatalog.custom(0x4F7CAC, com.chronolock.data.ThemeEffect.SUNSET).name)
        assertEquals("custom_4F7CAC_MIST+SUNSET+DUOTONE", com.chronolock.data.ThemeCatalog.custom(0x4F7CAC,
            setOf(com.chronolock.data.ThemeEffect.DUOTONE, com.chronolock.data.ThemeEffect.SUNSET, com.chronolock.data.ThemeEffect.MIST)).name)
        assertEquals(setOf(com.chronolock.data.ThemeEffect.MIST),
            com.chronolock.data.ThemeEffect.toggle(setOf(com.chronolock.data.ThemeEffect.PASTEL), com.chronolock.data.ThemeEffect.MIST))
        assertEquals(setOf(com.chronolock.data.ThemeEffect.PASTEL),
            com.chronolock.data.ThemeEffect.toggle(setOf(com.chronolock.data.ThemeEffect.PASTEL), com.chronolock.data.ThemeEffect.PASTEL))
        assertEquals(0xFF123456, com.chronolock.data.ThemeCatalog.custom(0x123456, com.chronolock.data.ThemeEffect.SOLID).background)
        com.chronolock.data.ThemeEffect.entries.forEach { assertTrue("Missing translation: ${it.word}", it.word in translationKeys) }
    }
    @Test fun missingOrUnknownPreference_fallsBackToDark() {
        assertEquals(AppTheme.DARK, AppTheme.fromStored(null))
        assertEquals(AppTheme.DARK, AppTheme.fromStored("removed-theme"))
    }
    private fun assertHue(theme: String, word: String, color: Long) {
        val expected = HUE_WORDS.getValue(word)
        val distance = abs(hueOf(color) - expected).let { minOf(it, 360 - it) }
        assertTrue("$theme: '$word' ($expected°) names a colour at ${hueOf(color)}° (%X)".format(color), distance <= 25)
    }
    private fun channels(color: Long) = listOf((color shr 16) and 255, (color shr 8) and 255, color and 255).map { it / 255.0 }
    private fun hueOf(color: Long): Int {
        val (r, g, b) = channels(color)
        val high = maxOf(r, g, b)
        val delta = high - minOf(r, g, b)
        if (delta == 0.0) return 0
        val hue = when (high) { r -> ((g - b) / delta).mod(6.0); g -> (b - r) / delta + 2; else -> (r - g) / delta + 4 } * 60
        return hue.roundToInt() % 360
    }
    private fun saturationOf(color: Long): Double {
        val (r, g, b) = channels(color)
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        return if (high == low) 0.0 else (high - low) / (1 - abs(high + low - 1))
    }
    private fun blend(from: Long, to: Long, amount: Double): Long {
        val a = channels(from)
        val b = channels(to)
        val mixed = a.indices.map { ((a[it] + (b[it] - a[it]) * amount) * 255).roundToInt().toLong() }
        return 0xFF000000L or (mixed[0] shl 16) or (mixed[1] shl 8) or mixed[2]
    }
    private fun androidx.compose.ui.graphics.Color.toLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

    private companion object {
        /** Hue of each colour word the catalog uses for a hue (copied from ThemeCatalog so a renamed word is caught). */
        val HUE_WORDS = mapOf("로즈" to 0, "코랄" to 15, "피치" to 30, "앰버" to 45, "레몬" to 60, "라임" to 90, "민트" to 120,
            "에메랄드" to 150, "틸" to 165, "아쿠아" to 180, "세룰리안" to 195, "스카이" to 210, "블루" to 225, "페리윙클" to 240,
            "인디고" to 255, "라벤더" to 270, "바이올렛" to 285, "릴락" to 300, "핑크" to 330)
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
