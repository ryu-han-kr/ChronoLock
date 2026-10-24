package com.chronolock.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Generated palettes beyond the built-in five. Families: Color Hunt trend picks, pastel tints, cool dusty tints,
 * duotone (one hue for the page, another for the accent), light and deep gradients, dual-hue gradients,
 * midnight solids and neutrals. Text, muted and accent colours are pushed toward black or white until they clear
 * WCAG AA against every background they can appear on, so a new seed can never produce unreadable text.
 */
object ThemeCatalog {
    const val BASIC = "기본"
    const val TREND = "트렌드"
    const val PASTEL = "파스텔"
    const val COOL = "쿨 톤"
    const val DUOTONE = "듀오톤"
    const val GRADIENT = "그라데이션"
    const val DUAL = "듀얼 그라데이션"
    const val DEEP = "딥 그라데이션"
    const val MIDNIGHT = "미드나잇"
    const val NEUTRAL = "뉴트럴"
    const val CUSTOM = "나만의 테마"
    const val LIVE = "움직이는 테마"

    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L

    /** Hue words at roughly 15° steps; generators pick the nearest word for a hue. */
    private val hueWords = listOf(0 to "로즈", 15 to "코랄", 30 to "피치", 45 to "앰버", 60 to "레몬", 90 to "라임", 120 to "민트",
        150 to "에메랄드", 165 to "틸", 180 to "아쿠아", 195 to "세룰리안", 210 to "스카이", 225 to "블루", 240 to "페리윙클",
        255 to "인디고", 270 to "라벤더", 285 to "바이올렛", 300 to "릴락", 330 to "핑크")

    private val hues = listOf(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330)
    /** Hue steps of a rainbow page after its first colour. */
    private val RAINBOW_STEPS = listOf(60, 120, 180, 240, 300)
    private val coolHues = listOf(150, 165, 180, 195, 210, 225, 240, 255, 270, 285, 300)

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

    /** Page hue paired with a contrasting accent hue. */
    private val duotonePairs = listOf(120 to 15, 210 to 45, 270 to 60, 330 to 165, 30 to 225, 90 to 285,
        180 to 0, 255 to 30, 150 to 330, 195 to 15, 240 to 45, 300 to 150)

    /** Two hues for a top-to-bottom gradient; the accent follows the second. */
    private val dualPairs = listOf(330 to 270, 165 to 255, 15 to 270, 210 to 330, 120 to 210, 45 to 330,
        180 to 285, 60 to 150, 285 to 15, 195 to 120, 240 to 165, 300 to 225)

    private class Solid(val id: String, val word: String, val hue: Int, val saturation: Double, val lightness: Double)

    private val midnightSolids = listOf(Solid("navy", "네이비", 220, .45, .14), Solid("charcoal", "차콜", 210, .08, .13),
        Solid("plum", "플럼", 300, .30, .14), Solid("forest", "포레스트", 150, .30, .12), Solid("slate", "슬레이트", 205, .18, .18),
        Solid("espresso", "에스프레소", 25, .35, .12), Solid("ink", "잉크", 240, .25, .10), Solid("wine", "와인", 345, .40, .13),
        Solid("teal", "틸", 175, .40, .12), Solid("indigo", "인디고", 255, .40, .14))

    private val neutralSolids = listOf(Solid("warm_gray", "웜 그레이", 30, .06, .90), Solid("cool_gray", "쿨 그레이", 220, .06, .90),
        Solid("beige", "베이지", 40, .30, .90), Solid("sand", "샌드", 45, .40, .88), Solid("stone", "스톤", 200, .08, .86),
        Solid("oat", "오트", 50, .22, .92), Solid("graphite", "그라파이트", 220, .05, .13), Solid("sepia", "세피아", 30, .30, .13))

    /**
     * Seeds that rebuild into the same page and accent as another seed (9 is seed 3 with the darkest colour shifted by
     * a few steps). They are left out of the catalog; their stored ids open the palette they repeat.
     */
    private val trendAliases = mapOf("trend_9" to "trend_3")

    /** [name] with a dropped duplicate id replaced by the palette it repeats; other names unchanged. */
    fun resolve(name: String?): String? = trendAliases[name] ?: name

    /**
     * Trend palettes are named page colour first, then accent ("민트 핑크"), since the page is what the tile mostly shows.
     * An accent that contrast nudging turned greyish is not named by its hue ("코랄" for a grey-brown button misleads).
     * A number is added only when two seeds look alike under the same words.
     */
    val trend: List<AppTheme> = trendSeeds.mapIndexed { index, seed -> fromSeed("trend_${index + 1}", listOf(TREND), seed) }
        .filter { it.name !in trendAliases }.let { themes ->
        val words = themes.map { theme ->
            listOfNotNull(pageWord(theme.background), colorWord(theme.primary).takeIf { saturation(theme.primary) >= .2 }).distinct()
        }
        themes.mapIndexed { index, theme ->
            val word = words[index]
            val duplicates = words.count { it == word }
            val ordinal = words.take(index + 1).count { it == word }
            theme.copy(label = listOf(TREND) + word + if (duplicates > 1) listOf("$ordinal") else emptyList())
        }
    }
    val pastel: List<AppTheme> = hues.map { hue -> tint("pastel_$hue", listOf(PASTEL, wordFor(hue)), hue, hue, .70, .90) }
    val cool: List<AppTheme> = coolHues.map { hue -> tint("cool_$hue", listOf(COOL, wordFor(hue)), hue, hue, .30, .84) }
    /** The accent is named by what it shows: a yellow accent darkened for contrast is "올리브" or "골드", not "레몬". */
    val duotone: List<AppTheme> = duotonePairs.map { (page, accent) ->
        tint("duo_${page}_$accent", emptyList(), page, accent, .60, .90).let { theme ->
            theme.copy(label = listOf(DUOTONE, wordFor(page), shadeWord(theme.primary) ?: wordFor(accent)))
        }
    }
    /**
     * Named by the top colour. The accent follows the bottom colour and usually reads as a deeper shade of it; when
     * contrast nudging turns it brown, gold or olive (a rose page with an olive button) that word is added.
     */
    val gradient: List<AppTheme> = hues.map { hue ->
        gradient("gradient_$hue", emptyList(), hue, (hue + 60) % 360).let { theme ->
            theme.copy(label = listOf(GRADIENT, wordFor(hue)) + listOfNotNull(shadeWord(theme.primary)))
        }
    }
    val dual: List<AppTheme> = dualPairs.map { (from, to) -> gradient("dual_${from}_$to", listOf(DUAL, wordFor(from), wordFor(to)), from, to) }
    /** Deep pages are dark, so warm hues read as brown or olive there rather than peach or lemon. */
    val deep: List<AppTheme> = hues.map { hue ->
        deep("deep_$hue", emptyList(), hue).let { theme -> theme.copy(label = listOf(DEEP, shadeWord(theme.background) ?: wordFor(hue))) }
    }
    val midnight: List<AppTheme> = midnightSolids.map { solid -> darkSolid("midnight_${solid.id}", listOf(MIDNIGHT, solid.word), solid) }
    val neutral: List<AppTheme> = neutralSolids.map { solid ->
        if (solid.lightness < .5) darkSolid("neutral_${solid.id}", listOf(NEUTRAL, solid.word), solid)
        else tint("neutral_${solid.id}", listOf(NEUTRAL, solid.word), solid.hue, solid.hue, solid.saturation, solid.lightness, accentSaturation = .40)
    }

    /** Pages whose colours slowly flow: rainbows and aurora-like drifts, built like custom palettes with the flow effect. */
    val live: List<AppTheme> = listOf(
        Triple("rainbow", listOf("무지개"), 0 to setOf(ThemeEffect.PASTEL, ThemeEffect.RAINBOW)),
        Triple("rainbow_night", listOf(MIDNIGHT, "무지개"), 0 to setOf(ThemeEffect.MIDNIGHT, ThemeEffect.RAINBOW)),
        Triple("aurora", listOf("오로라"), 160 to setOf(ThemeEffect.AURORA)),
        // Violet flowing into gold, with a gold button; the name says both (stored id unchanged).
        Triple("aurora_violet", listOf("오로라", "바이올렛", "골드"), 285 to setOf(ThemeEffect.DEEP, ThemeEffect.AURORA)),
        Triple("sunset", listOf("노을"), 20 to setOf(ThemeEffect.SUNSET)),
        Triple("ocean", listOf("아쿠아"), 190 to setOf(ThemeEffect.GRADIENT)),
        // A soft lavender-to-orchid drift; the complementary sweep it used to have ended in green with a green button.
        Triple("lavender", listOf("라벤더"), 270 to setOf(ThemeEffect.MIST, ThemeEffect.GRADIENT)),
    ).map { (id, words, look) ->
        val (hue, effects) = look
        combined("live_$id", listOf(LIVE) + words, hsl(hue, .7, .5), hue, 1.0, effects + ThemeEffect.FLOW).copy(premium = true)
    }

    val sections: List<Pair<String, List<AppTheme>>> = listOf(LIVE to live, TREND to trend, PASTEL to pastel, COOL to cool, DUOTONE to duotone,
        GRADIENT to gradient, DUAL to dual, DEEP to deep, MIDNIGHT to midnight, NEUTRAL to neutral)
    val all: List<AppTheme> = sections.flatMap { it.second }

    /** A word for a page colour: neutral words for near-white, greyish and pale yellow pages, else the nearest hue. */
    private fun pageWord(color: Long): String {
        val (r, g, b) = channels(color).map { it / 255.0 }
        val lightness = (max(r, max(g, b)) + min(r, min(g, b))) / 2
        val hue = hueOf(color)
        val yellowish = hue in 30..70
        return when {
            lightness >= .96 -> if (yellowish) "아이보리" else "화이트"
            saturation(color) < .25 -> if (hue in 90..270) "쿨 그레이" else "웜 그레이"
            yellowish && hue >= 35 && lightness >= .85 -> "크림"
            yellowish && hue >= 35 && lightness >= .7 -> "버터"
            else -> wordFor(hue)
        }
    }

    /**
     * Name of a custom palette: "custom_RRGGBB_EFFECT" or "custom_RRGGBB_EFFECT+EFFECT" in [ThemeEffect] order, so the
     * stored theme name alone rebuilds it. A single effect keeps the name it had before effects could be combined.
     */
    fun customName(rgb: Int, effects: Set<ThemeEffect>) =
        "custom_%06X_%s".format(rgb and 0xFFFFFF, ThemeEffect.entries.filter { it in effects }.joinToString("+") { it.name })

    fun customName(rgb: Int, effect: ThemeEffect) = customName(rgb, setOf(effect))

    /** The colour and effects inside a custom palette name, or null for any other name. */
    fun parseCustom(name: String?): Pair<Int, Set<ThemeEffect>>? {
        val match = Regex("custom_([0-9A-F]{6})_([A-Z]+(?:\\+[A-Z]+)*)").matchEntire(name ?: return null) ?: return null
        val effects = match.groupValues[2].split('+').map { word -> ThemeEffect.entries.firstOrNull { it.name == word } ?: return null }
        return match.groupValues[1].toInt(16) to ThemeEffect.combine(effects)
    }

    fun custom(rgb: Int, effect: ThemeEffect): AppTheme = custom(rgb, setOf(effect))

    /**
     * A palette built from any colour 000000-FFFFFF with [effects], at most one per [ThemeEffect.group]. Every effect
     * goes through the same contrast nudging as the catalog, so text stays readable whatever colour is picked. Grey
     * picks stay grey. One effect alone keeps the exact look it had before effects could be combined.
     */
    fun custom(rgb: Int, effects: Set<ThemeEffect>): AppTheme {
        val chosen = ThemeEffect.combine(effects).ifEmpty { setOf(ThemeEffect.PASTEL) }
        val base = 0xFF000000L or (rgb and 0xFFFFFF).toLong()
        val hue = hueOf(base)
        val grey = saturation(base) < .08
        val vivid = if (grey) 0.0 else 1.0
        val id = customName(rgb, chosen)
        val label = listOf(CUSTOM, "#%06X".format(rgb and 0xFFFFFF)) + ThemeEffect.entries.filter { it in chosen }.map { it.word }
        val effect = chosen.singleOrNull() ?: return combined(id, label, base, hue, vivid, chosen).copy(premium = false)
        return when (effect) {
            ThemeEffect.SOLID -> solid(id, label, base)
            ThemeEffect.PASTEL -> tint(id, label, hue, hue, .70 * vivid, .90, accentSaturation = .55 * vivid)
            ThemeEffect.MIST -> tint(id, label, hue, hue, .25 * vivid, .94, accentSaturation = .35 * vivid)
            ThemeEffect.GRADIENT -> gradient(id, label, hue, (hue + 40) % 360, .75 * vivid)
            ThemeEffect.COMPLEMENT -> gradient(id, label, hue, (hue + 180) % 360, .70 * vivid)
            ThemeEffect.SUNSET -> gradient(id, label, hue, (hue + 310) % 360, .80 * vivid)
            ThemeEffect.DUOTONE -> tint(id, label, hue, (hue + 180) % 360, .60 * vivid, .90, accentSaturation = .55 * vivid)
            ThemeEffect.DEEP -> deep(id, label, hue, saturation = .50 * vivid)
            ThemeEffect.AURORA -> deep(id, label, hue, (hue + 120) % 360, .55 * vivid)
            ThemeEffect.MIDNIGHT -> darkSolid(id, label, Solid(id, "", hue, .40 * vivid, .11))
            ThemeEffect.RAINBOW, ThemeEffect.FLOW -> combined(id, label, base, hue, vivid, chosen)
        }.copy(premium = false)
    }

    /**
     * Several effects at once: the page effect sets brightness and strength, a gradient effect adds the bottom colour
     * and a duotone moves the accent to the opposite hue. Without a page effect, aurora is dark and the rest are light.
     */
    private fun combined(id: String, label: List<String>, base: Long, hue: Int, vivid: Double, effects: Set<ThemeEffect>): AppTheme {
        val page = effects.firstOrNull { it.group == ThemeEffect.Group.PAGE }
        val sweep = effects.firstOrNull { it.group == ThemeEffect.Group.GRADIENT }
        val live = ThemeEffect.FLOW in effects
        // A flowing page needs a second colour to move; deep pages bring their own.
        val offset = sweep?.hueOffset ?: if (page == ThemeEffect.DEEP || live) ThemeEffect.DEEP.hueOffset else null
        val bandHues = (if (sweep == ThemeEffect.RAINBOW) RAINBOW_STEPS else listOfNotNull(offset)).map { (hue + it) % 360 }
        val accentHue = if (ThemeEffect.DUOTONE in effects) (hue + 180) % 360 else bandHues.lastOrNull() ?: hue
        fun build(dark: Boolean, background: Long, surface: Long, bands: List<Long>, primary: Long, text: Long, muted: Long, onPrimary: Long) =
            AppTheme(id, label, "", dark, background, surface, primary, onPrimary, text, muted, bands.lastOrNull(), stops = bands.dropLast(1), live = live)
        if (page == ThemeEffect.SOLID) {
            val light = contrast(base, BLACK) >= contrast(base, WHITE)
            val target = if (light) BLACK else WHITE
            // Hues of equal lightness differ in luminance (yellow vs blue), so the other colours are moved off the text colour.
            val bands = bandHues.map { ensure(hsl(it, saturation(base), lightness(base)), if (light) WHITE else BLACK, 8.0, target) }
            val surface = if (light) mix(base, WHITE, .45) else mix(base, BLACK, .12)
            val pages = spread(listOf(base) + bands) + surface
            val accentSaturation = if (vivid == 0.0) 0.0 else max(saturation(base), .5)
            val primary = ensure(hsl(accentHue, accentSaturation, if (light) .30 else .75), target, 4.5, *pages)
            val text = ensure(mix(base, target, .85), target, 7.0, *pages)
            val muted = ensure(mix(base, target, .55), target, 4.5, *pages)
            return build(!light, base, surface, bands, primary, text, muted, onPrimary(primary, base, WHITE, BLACK))
        }
        val dark = page == ThemeEffect.DEEP || page == ThemeEffect.MIDNIGHT || (page == null && sweep == ThemeEffect.AURORA)
        if (dark) {
            val saturation = (if (page == ThemeEffect.MIDNIGHT) .40 else .55) * vivid
            val lightness = if (page == ThemeEffect.MIDNIGHT) .11 else .12
            val background = hsl(hue, saturation, lightness)
            val bands = bandHues.map { hsl(it, saturation, if (sweep == ThemeEffect.RAINBOW) .20 else .24) }
            val surface = hsl(hue, saturation * .7, lightness + .08)
            val pages = spread(listOf(background) + bands) + surface
            val primary = ensure(hsl(accentHue, min(max(saturation * 1.4, .45 * vivid), 1.0), .72), WHITE, 4.5, *pages)
            val text = ensure(hsl(hue, .2, .93), WHITE, 7.0, *pages)
            val muted = ensure(hsl(hue, .15, .70), WHITE, 4.5, *pages)
            return build(true, background, surface, bands, primary, text, muted, onPrimary(primary, background, BLACK, WHITE))
        }
        val saturation = when (page) { ThemeEffect.MIST -> .25; ThemeEffect.PASTEL -> .70; else -> .75 } * vivid
        val lightness = when (page) { ThemeEffect.MIST -> .94; ThemeEffect.PASTEL -> .90; else -> .88 }
        val background = hsl(hue, saturation, lightness)
        val bands = bandHues.map { hsl(it, saturation, lightness - .04) }
        val surface = hsl(hue, min(saturation + .05, 1.0), .975)
        val pages = spread(listOf(background) + bands) + surface
        val primary = ensure(hsl(accentHue, max(saturation * .73, .35 * vivid), .40), BLACK, 4.5, *pages)
        val text = ensure(hsl(hue, .25, .14), BLACK, 7.0, *pages)
        val muted = ensure(hsl(hue, .12, .40), BLACK, 4.5, *pages)
        return build(false, background, surface, bands, primary, text, muted, onPrimary(primary, WHITE, surface, BLACK))
    }

    private fun lightness(color: Long): Double {
        val (r, g, b) = channels(color).map { it / 255.0 }
        return (max(r, max(g, b)) + min(r, min(g, b))) / 2
    }

    /** The picked colour itself as the page; text and accent go to whichever of black or white reads on it. */
    private fun solid(id: String, label: List<String>, base: Long): AppTheme {
        val light = contrast(base, BLACK) >= contrast(base, WHITE)
        val target = if (light) BLACK else WHITE
        // Cards move away from the text colour so text keeps at least the contrast it has on the page.
        val surface = if (light) mix(base, WHITE, .45) else mix(base, BLACK, .12)
        val hue = hueOf(base)
        val accentSaturation = if (saturation(base) < .08) 0.0 else max(saturation(base), .5)
        val primary = ensure(hsl(hue, accentSaturation, if (light) .30 else .75), target, 4.5, base, surface)
        val text = ensure(mix(base, target, .85), target, 7.0, base, surface)
        val muted = ensure(mix(base, target, .55), target, 4.5, base, surface)
        return AppTheme(id, label, "", !light, base, surface, primary, onPrimary(primary, base, WHITE, BLACK), text, muted)
    }

    private fun wordFor(hue: Int): String = hueWords.minByOrNull { (word, _) -> hueDistance(hue, word) }!!.second

    /**
     * A word for a warm colour that is too dark to look like its hue word: contrast nudging turns peach, amber and
     * lemon into brown, gold and olive, and a deep page in those hues is brown or olive too. Null for other colours.
     */
    private fun shadeWord(color: Long): String? {
        if (lightness(color) >= .36 || saturation(color) < .2) return null
        return when (hueOf(color)) {
            in 20 until 35 -> "브라운"
            in 35 until 50 -> "골드"
            in 50..70 -> "올리브"
            else -> null
        }
    }

    /** The word for a colour shown as is: a dark warm shade first, else the nearest hue word. */
    private fun colorWord(color: Long): String = shadeWord(color) ?: wordFor(hueOf(color))

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

    /** Light page in [hue] with the accent taken from [accentHue]. */
    private fun tint(id: String, label: List<String>, hue: Int, accentHue: Int, pageSaturation: Double, pageLightness: Double,
        accentSaturation: Double = .55): AppTheme {
        val background = hsl(hue, pageSaturation, pageLightness)
        val surface = hsl(hue, min(pageSaturation + .05, 1.0), .975)
        val primary = ensure(hsl(accentHue, accentSaturation, .40), BLACK, 4.5, background, surface)
        val text = ensure(hsl(hue, .25, .14), BLACK, 7.0, background, surface)
        val muted = ensure(hsl(hue, .12, .40), BLACK, 4.5, background, surface)
        return AppTheme(id, label, "", false, background, surface, primary, onPrimary(primary, WHITE, surface, BLACK), text, muted, premium = true)
    }

    private fun gradient(id: String, label: List<String>, from: Int, to: Int, saturation: Double = .75): AppTheme {
        val background = hsl(from, saturation, .88)
        val end = hsl(to, saturation, .84)
        val surface = hsl(from, saturation * .8, .975)
        val primary = ensure(hsl(to, saturation * .73, .38), BLACK, 4.5, *spread(listOf(background, end)), surface)
        val text = ensure(hsl(from, .25, .14), BLACK, 7.0, *spread(listOf(background, end)), surface)
        val muted = ensure(hsl(from, .12, .40), BLACK, 4.5, *spread(listOf(background, end)), surface)
        return AppTheme(id, label, "", false, background, surface, primary, onPrimary(primary, WHITE, surface, BLACK), text, muted, end, premium = true)
    }

    private fun deep(id: String, label: List<String>, hue: Int, endHue: Int = (hue + 60) % 360, saturation: Double = .50): AppTheme {
        val background = hsl(hue, saturation, .12)
        val end = hsl(endHue, saturation, .24)
        val surface = hsl(hue, saturation * .7, .20)
        val primary = ensure(hsl(endHue, saturation * 1.4, .72), WHITE, 4.5, *spread(listOf(background, end)), surface)
        val text = ensure(hsl(hue, .2, .93), WHITE, 7.0, *spread(listOf(background, end)), surface)
        val muted = ensure(hsl(hue, .15, .70), WHITE, 4.5, *spread(listOf(background, end)), surface)
        return AppTheme(id, label, "", true, background, surface, primary, onPrimary(primary, background, BLACK, WHITE), text, muted, end, premium = true)
    }

    private fun darkSolid(id: String, label: List<String>, solid: Solid): AppTheme {
        val background = hsl(solid.hue, solid.saturation, solid.lightness)
        val surface = hsl(solid.hue, solid.saturation, solid.lightness + .06)
        val primary = ensure(hsl(solid.hue, max(solid.saturation, .45), .72), WHITE, 4.5, background, surface)
        val text = ensure(hsl(solid.hue, min(solid.saturation, .15), .92), WHITE, 7.0, background, surface)
        val muted = ensure(hsl(solid.hue, min(solid.saturation, .12), .70), WHITE, 4.5, background, surface)
        return AppTheme(id, label, "", true, background, surface, primary, onPrimary(primary, background, BLACK, WHITE), text, muted, premium = true)
    }

    /**
     * The colours a gradient through [pages] shows: each stop and nine blends between neighbours. Text is checked on all
     * of them because a blend of two readable stops can be darker or lighter than both.
     */
    private fun spread(pages: List<Long>): LongArray =
        (if (pages.size < 2) pages else pages.zipWithNext().flatMap { (a, b) -> (0..10).map { mix(a, b, it / 10.0) } }).toLongArray()

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
        val h = ((hue % 360 + 360) % 360) / 60.0
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

/**
 * Looks a custom palette can take; [word] is the translatable chip label. Effects of different [group]s combine;
 * [hueOffset] is where a gradient's bottom colour sits on the colour wheel.
 */
enum class ThemeEffect(val word: String, val group: Group, val hueOffset: Int? = null) {
    SOLID("단색", Group.PAGE), PASTEL("파스텔", Group.PAGE), MIST("안개", Group.PAGE), GRADIENT("그라데이션", Group.GRADIENT, 40),
    COMPLEMENT("보색 그라데이션", Group.GRADIENT, 180), SUNSET("노을", Group.GRADIENT, 310), DUOTONE("듀오톤", Group.ACCENT),
    DEEP("딥 그라데이션", Group.PAGE, 60), AURORA("오로라", Group.GRADIENT, 120), MIDNIGHT("미드나잇", Group.PAGE),
    RAINBOW("무지개", Group.GRADIENT), FLOW("흐름", Group.MOTION);

    /** Page brightness and strength, the other page colours, the accent hue, and whether the page moves. */
    enum class Group { PAGE, GRADIENT, ACCENT, MOTION }

    companion object {
        /** [effects] with at most one per group; a later effect replaces an earlier one of its group. */
        fun combine(effects: Iterable<ThemeEffect>): Set<ThemeEffect> =
            effects.associateBy { it.group }.values.sortedBy { it.ordinal }.toSet()

        /** [current] with [effect] switched: on replaces the effect of its group, off removes it unless it is the last. */
        fun toggle(current: Set<ThemeEffect>, effect: ThemeEffect): Set<ThemeEffect> =
            if (effect in current) (current - effect).ifEmpty { current } else combine(current + effect)
    }
}
