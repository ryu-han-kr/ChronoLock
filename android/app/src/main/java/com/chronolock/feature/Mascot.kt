package com.chronolock.feature

import android.annotation.SuppressLint
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** How the mascot feels about the usage it is looking at. */
enum class Mood { SLEEPY, RELAXED, HAPPY, SURPRISED, DIZZY }

private const val HOUR = 3_600_000L

/** Average daily usage to mood: under 1h relaxed, under 2.5h happy, under 5h surprised, beyond that dizzy. */
fun moodFor(averageMillisPerDay: Long, hasRecords: Boolean): Mood = when {
    !hasRecords -> Mood.SLEEPY
    averageMillisPerDay < HOUR -> Mood.RELAXED
    averageMillisPerDay < HOUR * 5 / 2 -> Mood.HAPPY
    averageMillisPerDay < HOUR * 5 -> Mood.SURPRISED
    else -> Mood.DIZZY
}

/**
 * What the mascot says, per mood. Korean source strings; each goes through tr() so a line without a translation
 * still shows in Korean. Several lines per mood rotate by date. Edit here (see docs/MASCOT.md).
 */
object MascotLines {
    val lines: Map<Mood, List<String>> = mapOf(
        Mood.SLEEPY to listOf("아직 기록이 없어요. 쿨쿨…"),
        Mood.RELAXED to listOf("여유로운 하루네요. 이대로만!", "폰보다 하루가 더 길었네요."),
        Mood.HAPPY to listOf("딱 좋은 페이스예요.", "오늘도 균형 잡힌 하루!"),
        Mood.SURPRISED to listOf("어, 생각보다 많이 썼어요!", "볼이 터질 것 같아요. 잠깐만요!"),
        Mood.DIZZY to listOf("눈이 빙글빙글… 잠깐 쉬어요.", "오늘은 폰을 내려놓을 시간이에요."),
    )
}

/** The first line of the mood; stable, used by tests and content descriptions. */
internal val Mood.message: String get() = MascotLines.lines.getValue(this).first()

/** The line for [day]: rotates through the mood's lines so it changes across days but not within one. */
internal fun Mood.line(day: LocalDate): String {
    val options = MascotLines.lines.getValue(this)
    return options[day.toEpochDay().mod(options.size)]
}

/**
 * The history mascot. Draws a PNG named `mascot_<mood>` from res/drawable when one exists (artist-supplied), and the
 * built-in hamster otherwise. Either way it breathes, and wobbles when dizzy.
 */
@Composable
internal fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val context = LocalContext.current
    @SuppressLint("DiscouragedApi")
    val artwork = remember(mood) {
        context.resources.getIdentifier("mascot_${mood.name.lowercase()}", "drawable", context.packageName).takeIf { it != 0 }
    }
    val transition = rememberInfiniteTransition(label = "mascot")
    val breath by transition.animateFloat(1f, 1.035f,
        infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse), label = "breath")
    val wobble by transition.animateFloat(-7f, 7f,
        infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "wobble")
    val description = tr(mood.message)
    val tagged = modifier.size(size).semantics { contentDescription = description }.testTag("hamster-${mood.name.lowercase()}")
    if (artwork != null) {
        Image(painterResource(artwork), contentDescription = null, modifier = tagged.graphicsLayer {
            scaleX = breath; scaleY = breath
            rotationZ = if (mood == Mood.DIZZY) wobble else 0f
            transformOrigin = TransformOrigin(.5f, .95f)
        })
    } else Hamster(mood, tagged, breath, wobble)
}

// The hamster keeps its own fur so it reads as the same character on every theme.
private val Fur = Color(0xFFEABC86)
private val FurShade = Color(0xFFD69C63)
private val Cream = Color(0xFFFFF4E3)
private val Pink = Color(0xFFF3A4AA)
private val Ink = Color(0xFF3B2B22)

/**
 * The built-in hamster drawn with primitives: ears, a blobby body, puffed cheek pouches, paws, whiskers and a face
 * that changes with [mood]. Blinks when its eyes are open.
 */
@Composable
private fun Hamster(mood: Mood, modifier: Modifier, breath: Float, wobble: Float) {
    val transition = rememberInfiniteTransition(label = "hamster")
    val blink by transition.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 4200
        1f at 0
        1f at 3800
        .12f at 3900
        1f at 4000
    }), label = "blink")
    Canvas(modifier) {
        val u = this.size.width / 100f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        rotate(if (mood == Mood.DIZZY) wobble else 0f, pivot = o(50f, 92f)) {
            scale(breath, breath, pivot = o(50f, 94f)) {
                val earLift = if (mood == Mood.SURPRISED) -5f else 0f
                listOf(25f, 75f).forEach { x ->
                    drawCircle(FurShade, 12f * u, o(x, 29f + earLift))
                    drawCircle(Pink, 6.5f * u, o(x, 30f + earLift))
                }
                drawOval(Fur, topLeft = o(12f, 26f), size = Size(76f * u, 68f * u))
                drawOval(Cream, topLeft = o(31f, 62f), size = Size(38f * u, 28f * u))
                val pouch = if (mood == Mood.SURPRISED) 12.5f else 11f
                drawCircle(Cream, pouch * u, o(27f, 67f))
                drawCircle(Cream, pouch * u, o(73f, 67f))
                val blush = if (mood == Mood.HAPPY || mood == Mood.RELAXED) .75f else .45f
                drawCircle(Pink.copy(alpha = blush), 7f * u, o(26f, 69f))
                drawCircle(Pink.copy(alpha = blush), 7f * u, o(74f, 69f))
                drawCircle(Cream, 6f * u, o(41f, 90f))
                drawCircle(Cream, 6f * u, o(59f, 90f))
                whiskers(u)
                drawCircle(Pink, 3.2f * u, o(50f, 60f))
                mouth(mood, u)
                eyes(mood, blink, u)
            }
        }
        if (mood == Mood.SLEEPY) sleepMarks(u)
    }
}

private fun DrawScope.o(x: Float, y: Float, u: Float) = Offset(x * u, y * u)

private fun DrawScope.whiskers(u: Float) {
    val stroke = 1.4f * u
    val color = Ink.copy(alpha = .45f)
    listOf(-1f, 1f).forEach { side ->
        val cheek = 50f + side * 24f
        drawLine(color, o(cheek, 63f, u), o(cheek + side * 15f, 59f, u), stroke, StrokeCap.Round)
        drawLine(color, o(cheek, 67f, u), o(cheek + side * 16f, 68f, u), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.mouth(mood: Mood, u: Float) {
    val stroke = Stroke(1.9f * u, cap = StrokeCap.Round)
    when (mood) {
        Mood.RELAXED, Mood.HAPPY -> listOf(45f, 50f).forEach { x ->
            drawArc(Ink, 0f, 180f, false, topLeft = o(x, 61.5f, u), size = Size(5f * u, 4.5f * u), style = stroke)
        }
        Mood.SURPRISED -> drawOval(Ink, topLeft = o(47f, 64f, u), size = Size(6f * u, 7f * u))
        Mood.DIZZY -> drawPath(Path().apply {
            moveTo(44f * u, 67f * u)
            quadraticTo(47f * u, 63.5f * u, 50f * u, 67f * u)
            quadraticTo(53f * u, 70.5f * u, 56f * u, 67f * u)
        }, Ink, style = stroke)
        Mood.SLEEPY -> drawLine(Ink, o(47f, 66f, u), o(53f, 66f, u), 1.8f * u, StrokeCap.Round)
    }
}

private fun DrawScope.eyes(mood: Mood, blink: Float, u: Float) {
    val stroke = Stroke(2.6f * u, cap = StrokeCap.Round)
    listOf(38f, 62f).forEach { x ->
        when (mood) {
            Mood.HAPPY -> {
                drawOval(Ink, topLeft = o(x - 4.2f, 51f - 4.2f * blink, u), size = Size(8.4f * u, 8.4f * u * blink))
                if (blink > .5f) drawCircle(Color.White, 1.5f * u, o(x + 1.4f, 49.4f, u))
            }
            Mood.SURPRISED -> {
                drawCircle(Color.White, 7.5f * u, o(x, 50f, u))
                drawCircle(Ink, 3.6f * u, o(x, 50.5f, u))
                drawCircle(Color.White, 1.3f * u, o(x + 1.4f, 49f, u))
                drawArc(Ink, 200f, 140f, false, topLeft = o(x - 7f, 36f, u), size = Size(14f * u, 7f * u), style = Stroke(2.2f * u, cap = StrokeCap.Round))
            }
            Mood.RELAXED -> drawArc(Ink, 200f, 140f, false, topLeft = o(x - 6f, 46.5f, u), size = Size(12f * u, 10f * u), style = stroke)
            Mood.SLEEPY -> drawArc(Ink, 20f, 140f, false, topLeft = o(x - 6f, 47f, u), size = Size(12f * u, 8f * u), style = Stroke(2.4f * u, cap = StrokeCap.Round))
            Mood.DIZZY -> {
                drawLine(Ink, o(x - 4f, 47f, u), o(x + 4f, 55f, u), 2.6f * u, StrokeCap.Round)
                drawLine(Ink, o(x + 4f, 47f, u), o(x - 4f, 55f, u), 2.6f * u, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.sleepMarks(u: Float) {
    listOf(Triple(78f, 30f, 5f), Triple(86f, 20f, 7f), Triple(94f, 8f, 9f)).forEach { (x, y, w) ->
        drawPath(Path().apply {
            moveTo(x * u, y * u); lineTo((x + w) * u, y * u); lineTo(x * u, (y + w) * u); lineTo((x + w) * u, (y + w) * u)
        }, Ink.copy(alpha = .6f), style = Stroke(1.8f * u, cap = StrokeCap.Round))
    }
}
