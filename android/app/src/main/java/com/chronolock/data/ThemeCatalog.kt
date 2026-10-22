package com.chronolock.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Generated palettes beyond the built-in five: Color Hunt trend picks, pastel tints and light/deep gradients.
 * Text, muted and accent colours are pushed toward black or white until they clear WCAG AA against every
 * background they can appear on, so a new seed can never produce unreadable text.
 */
object ThemeCatalog {
    const val BASIC = "기본"
    const val TREND = "트렌드"
    const val PASTEL = "파스텔"
    const val GRADIENT = "그라데이션"
    const val DEEP = "딥 그라데이션"

    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L

    private val hues = listOf(0 to "로즈", 30 to "피치", 60 to "레몬", 90 to "라임", 120 to "민트", 150 to "에메랄드",
        180 to "아쿠아", 210 to "스카이", 240 to "페리윙클", 270 to "라벤더", 300 to "릴락", 330 to "핑크")

    /** Four-colour seeds sampled from Color Hunt (2026-10). Order within a seed does not matter. */
    private val trendSeeds = listOf(
        listOf(0x5D7E85, 0x4AA9A6, 0xA5D4AD, 0xDDECC1), listOf(0xF6E4E3, 0x6FA5BC, 0x607DAE, 0x1B142F),
        listOf(0x9EDAB2, 0xC3E4B2, 0xEFE2AB, 0xF0C1BA), listOf(0xD8BB92, 0xF1E7DA, 0xF7EFE5, 0xC1CEDC),
        listOf(0x3B2DCA, 0x9157C4, 0xDD66E8, 0x7ADAD8), listOf(0x180C0B, 0x702173, 0xEA2D85, 0xF087A7),
        listOf(0x0C2C30, 0x17595B, 0xE0D88C, 0xF0EBCF), listOf(0x77B0D0, 0x89C78E, 0xE9D77C, 0xF2EEDC),
        listOf(0x9CD9B0, 0xC5E4B2, 0xEFE2AB, 0xF0C1BA), listOf(0xE1DEAD, 0xCF8EDD, 0xE8A9A7, 0xEDE6E5),
        listOf(0xF0FFFD, 0xBEFDF9, 0x90F1FD, 0x5DC5FB), listOf(0xAB8C76, 0xCCAC95, 0xE1CBA8, 0xFBF5E2),
        listOf(0xFDFDF3, 0xFABA46, 0xFCE348, 0xBAFD8D), listOf(0x6EBF8F, 0xBDEFC4, 0xF7C4D6, 0xF983AA),
        listOf(0xC4FDF0, 0xEDFFC9, 0xFCE7C3, 0xFBBEBC), listOf(0xFBF8F2, 0xF6EDE0, 0xE9DCC5, 0x61B8B8),
    )

    /** Trend palettes are named after the accent's nearest hue; repeated hues get a running number. */
    val trend: List<AppTheme> = trendSeeds.mapIndexed { index, seed -> fromSeed("trend_${index + 1}", listOf(TREND), seed) }.let { themes ->
        val words = themes.map { hues.minByOrNull { (hue, _) -> hueDistance(hueOf(it.primary), hue) }!!.second }
        themes.mapIndexed { index, theme ->
            val word = words[index]
            val duplicates = words.count { it == word }
            val ordinal = words.take(index + 1).count { it == word }
            theme.copy(label = if (duplicates > 1) listOf(TREND, word, "$ordinal") else listOf(TREND, word))
        }
    }
    val pastel: List<AppTheme> = hues.map { (hue, word) -> pastel("pastel_$hue", listOf(PASTEL, word), hue) }
    val gradient: List<AppTheme> = hues.map { (hue, word) -> gradient("gradient_$hue", listOf(GRADIENT, word), hue) }
    val deep: List<AppTheme> = hues.map { (hue, word) -> deep("deep_$hue", listOf(DEEP, word), hue) }

    val all: List<AppTheme> = trend + pastel + gradient + deep
    val sections: List<Pair<String, List<AppTheme>>> = listOf(TREND to trend, PASTEL to pastel, GRADIENT to gradient, DEEP to deep)

    private fun fromSeed(id: String, label: List<String>, seed: List<Int>): AppTheme {
        val colors = seed.map { 0xFF000000L or it.toLong() }
        val byLight = colors.sortedBy { luminance(it) }
        val lightest = byLight.last()
        return if (luminance(lightest) >= .35) {
            val background = lightest
            val surface = mix(background, WHITE, .5)
            val accent = colors.filter { it != background }.maxByOrNull { saturation(it) * (1 - luminance(it)) } ?: byLight.first()
            val primary = ensure(accent, BLACK, 4.5, background, surface)
            val text = ensure(mix(byLight.first(), BLACK, .4), BLACK, 7.0, background, surface)
            val muted = ensure(mix(text, background, .45), BLACK, 4.5, background, surface)
            AppTheme(id, label, "", false, background, surface, primary, onPrimary(primary, WHITE, surface, BLACK), text, muted, premium = true)
        } else {
            val background = byLight.first()
            val surface = mix(background, WHITE, .08)
            val accent = colors.filter { it != background }.maxByOrNull { saturation(it) } ?: lightest
            val primary = ensure(accent, WHITE, 4.5, background, surface)
            val text = ensure(mix(lightest, WHITE, .3), WHITE, 7.0, background, surface)
            val muted = ensure(mix(text, background, .4), WHITE, 4.5, background, surface)
            AppTheme(id, label, "", true, background, surface, primary, onPrimary(primary, background, BLACK, WHITE), text, muted, premium = true)
        }
    }

    private fun pastel(id: String, label: List<String>, hue: Int): AppTheme {
        val background = hsl(hue, .70, .90)
        val surface = hsl(hue, .75, .975)
        val primary = ensure(hsl(hue, .5, .42), BLACK, 4.5, background, surface)
        val text = ensure(hsl(hue, .25, .14), BLACK, 7.0, background, surface)
        val muted = ensure(hsl(hue, .12, .42), BLACK, 4.5, background, surface)
        return AppTheme(id, label, "", false, background, surface, primary, onPrimary(primary, WHITE, surface, BLACK), text, muted, premium = true)
    }

    private fun gradient(id: String, label: List<String>, hue: Int): AppTheme {
        val background = hsl(hue, .75, .88)
        val end = hsl((hue + 60) % 360, .75, .84)
        val surface = hsl(hue, .6, .975)
        val primary = ensure(hsl(hue, .5, .40), BLACK, 4.5, background, end, surface)
        val text = ensure(hsl(hue, .25, .14), BLACK, 7.0, background, end, surface)
        val muted = ensure(hsl(hue, .12, .40), BLACK, 4.5, background, end, surface)
        return AppTheme(id, label, "", false, background, surface, primary, onPrimary(primary, WHITE, surface, BLACK), text, muted, end, premium = true)
    }

    private fun deep(id: String, label: List<String>, hue: Int): AppTheme {
        val background = hsl(hue, .50, .12)
        val end = hsl((hue + 60) % 360, .50, .24)
        val surface = hsl(hue, .35, .20)
        val primary = ensure(hsl(hue, .7, .72), WHITE, 4.5, background, end, surface)
        val text = ensure(hsl(hue, .2, .93), WHITE, 7.0, background, end, surface)
        val muted = ensure(hsl(hue, .15, .70), WHITE, 4.5, background, end, surface)
        return AppTheme(id, label, "", true, background, surface, primary, onPrimary(primary, background, BLACK, WHITE), text, muted, end, premium = true)
    }

    /** First candidate readable on [primary]; the last one is the fallback. */
    private fun onPrimary(primary: Long, vararg candidates: Long): Long =
        candidates.firstOrNull { contrast(primary, it) >= 4.5 } ?: candidates.last()

    /** Mixes [color] toward [target] in 5% steps until it reaches [ratio] against every background. */
    private fun ensure(color: Long, target: Long, ratio: Double, vararg backgrounds: Long): Long {
        var mixed = color
        var amount = 0.0
        while (backgrounds.any { contrast(mixed, it) < ratio } && amount < 1.0) {
            amount += .05
            mixed = mix(color, target, amount)
        }
        return mixed
    }

    private fun channels(color: Long) = intArrayOf(((color shr 16) and 255).toInt(), ((color shr 8) and 255).toInt(), (color and 255).toInt())

    private fun argb(r: Int, g: Int, b: Int): Long =
        0xFF000000L or (r.coerceIn(0, 255).toLong() shl 16) or (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong()

    private fun mix(from: Long, to: Long, amount: Double): Long {
        val a = channels(from)
        val b = channels(to)
        return argb((a[0] + (b[0] - a[0]) * amount).roundToInt(), (a[1] + (b[1] - a[1]) * amount).roundToInt(),
            (a[2] + (b[2] - a[2]) * amount).roundToInt())
    }

    private fun hsl(hue: Int, saturation: Double, lightness: Double): Long {
        val c = (1 - abs(2 * lightness - 1)) * saturation
        val h = (hue % 360) / 60.0
        val x = c * (1 - abs(h % 2 - 1))
        val (r, g, b) = when {
            h < 1 -> Triple(c, x, 0.0)
            h < 2 -> Triple(x, c, 0.0)
            h < 3 -> Triple(0.0, c, x)
            h < 4 -> Triple(0.0, x, c)
            h < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = lightness - c / 2
        return argb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    private fun hueOf(color: Long): Int {
        val (r, g, b) = channels(color).map { it / 255.0 }
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val delta = high - low
        if (delta == 0.0) return 0
        val hue = when (high) {
            r -> ((g - b) / delta) % 6
            g -> (b - r) / delta + 2
            else -> (r - g) / delta + 4
        } * 60
        return ((hue + 360) % 360).roundToInt()
    }

    private fun hueDistance(a: Int, b: Int): Int = min(abs(a - b), 360 - abs(a - b))

    private fun saturation(color: Long): Double {
        val (r, g, b) = channels(color).map { it / 255.0 }
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val delta = high - low
        return if (delta == 0.0) 0.0 else delta / (1 - abs(high + low - 1))
    }

    fun luminance(color: Long): Double {
        fun linear(value: Int): Double {
            val v = value / 255.0
            return if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        }
        val (r, g, b) = channels(color)
        return .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b)
    }

    fun contrast(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + .05) / (min(a, b) + .05)
    }
}
