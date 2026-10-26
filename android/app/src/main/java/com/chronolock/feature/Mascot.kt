package com.chronolock.feature

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronolock.data.MascotKind
import com.chronolock.data.Pose
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

/** The pose that shows [this] mood: asleep without records, proud when light, startled or crying as usage grows. */
internal val Mood.pose: Pose get() = when (this) {
    Mood.SLEEPY -> Pose.SLEEP
    Mood.RELAXED -> Pose.SUCCESS
    Mood.HAPPY -> Pose.DEFAULT
    Mood.SURPRISED -> Pose.WARNING
    Mood.DIZZY -> Pose.LIMIT
}

/** "Reduce motion" from settings: moving themes and the breathing character hold still. */
internal val LocalReduceMotion = staticCompositionLocalOf { false }

/** The character chosen in settings; screens draw it through [MascotArt]. */
internal val LocalMascot = staticCompositionLocalOf { MascotKind.DEFAULT }

/** The history mascot: the chosen character in the pose of [mood]. It breathes, and wobbles when dizzy. */
@Composable
internal fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val description = tr(mood.message)
    MascotArt(mood.pose, modifier.semantics { contentDescription = description }.testTag("mascot-${mood.name.lowercase()}"), size,
        wobble = mood == Mood.DIZZY)
}

/**
 * The chosen character (or [kind]) in [pose], alive but calm: its belly swells and settles while the head stays put
 * ([BreathingArt]), it leans slowly from side to side, sleeps slower, now and then hops when proud or flinches when
 * warned, and sways further when dizzy.
 * Each one starts at its own point in the cycle so characters side by side do not move in step, and a new pose fades in.
 * Decorative unless the caller adds a content description.
 */
@Composable
internal fun MascotArt(pose: Pose, modifier: Modifier = Modifier, size: Dp = 96.dp, kind: MascotKind = LocalMascot.current, wobble: Boolean = false) {
    if (LocalReduceMotion.current) {
        Image(painterResource(kind.art(pose)), contentDescription = null, modifier = modifier.size(size))
        return
    }
    val offset = remember { StartOffset(Random.nextInt(BREATH_MS)) }
    val transition = rememberInfiniteTransition(label = "mascot")
    val asleep = pose == Pose.SLEEP
    val breathMs = if (asleep) BREATH_MS * 3 / 2 else BREATH_MS
    // Inhale over two fifths of the cycle, exhale over the rest, as resting breath does.
    val breath by transition.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = breathMs
        0f at 0 using EaseInOutSine
        1f at breathMs * 2 / 5 using EaseInOutSine
    }, initialStartOffset = offset), label = "breath")
    // An occasional beat: a small hop for success, a quick flinch for a warning; still the rest of the time.
    val beat by transition.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = BEAT_MS
        0f at 0
        0f at BEAT_MS - 600
        1f at BEAT_MS - 450 using FastOutSlowInEasing
        0f at BEAT_MS - 250 using EaseInOutSine
        -.35f at BEAT_MS - 150
        0f at BEAT_MS - 60
    }, initialStartOffset = offset), label = "beat")
    // A slow lean from side to side keeps a still character alive; dizzy leans further and faster.
    val sway by transition.animateFloat(-1f, 1f,
        infiniteRepeatable(tween(if (wobble) 2600 else 4200, easing = EaseInOutSine), RepeatMode.Reverse, offset), label = "sway")
    val depth = if (asleep) .16f else .12f
    val lean = when { wobble -> 6f; asleep -> .6f; else -> 1.2f }
    Crossfade(pose to kind, modifier.size(size), animationSpec = tween(280), label = "pose") { (shown, character) ->
        BreathingArt(character.art(shown), { breath * depth }, Modifier.size(size).graphicsLayer {
            rotationZ = sway * lean
            when (shown) {
                Pose.SUCCESS -> { translationY = -beat.coerceAtLeast(0f) * .12f * this.size.height; scaleY += beat.coerceAtMost(0f) * .12f }
                Pose.WARNING -> if (beat > .05f) rotationZ += sin(beat * 3 * PI).toFloat() * 5f
                else -> Unit
            }
            transformOrigin = TransformOrigin(.5f, .95f)
        })
    }
}

private const val BREATH_MS = 3_600
private const val BEAT_MS = 3_600
private const val MESH = 12

/**
 * [art] drawn through a mesh so only the body breathes: around the belly the sides swell outward and the chest rises a
 * little, while the head and the feet keep their place. [swell] is the current breath, 0 to about 0.16.
 */
@Composable
private fun BreathingArt(art: Int, swell: () -> Float, modifier: Modifier) {
    val bitmap = ImageBitmap.imageResource(art).asAndroidBitmap()
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val vertices = remember { FloatArray((MESH + 1) * (MESH + 1) * 2) }
    Canvas(modifier) {
        val amount = swell()
        var k = 0
        for (row in 0..MESH) for (col in 0..MESH) {
            val u = col / MESH.toFloat(); val v = row / MESH.toFloat()
            // Strongest at the belly, fading out before the head and pinned at the feet.
            val belly = exp(-((v - .66f) / .16f).let { it * it }) * ((1f - v) / .08f).coerceAtMost(1f)
            vertices[k++] = (u + (u - .5f) * amount * belly) * size.width
            vertices[k++] = (v - amount * .12f * belly) * size.height
        }
        drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(bitmap, MESH, MESH, vertices, 0, null, 0, paint) }
    }
}

/** A card title with the character beside it, doing what the card is about. */
@Composable
internal fun MascotTitle(title: String, pose: Pose, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        MascotArt(pose, Modifier.testTag("mascot-title-${pose.name.lowercase()}"), size = 56.dp)
    }
}

/** What the Today character does and says: the strongest of lock, focus, a used-up limit, a pause, bedtime and so on. */
internal data class TodayMood(val pose: Pose, val line: String)

internal fun todayMood(locked: Boolean, focusing: Boolean, limitReached: Boolean, nearLimit: Boolean, paused: Boolean,
    hour: Int, hasRules: Boolean): TodayMood = when {
    locked -> TodayMood(Pose.LOCK, "잠금 중이에요. 제가 꼭 지키고 있을게요.")
    focusing -> TodayMood(Pose.FOCUS, "집중하는 중! 조용히 응원할게요.")
    limitReached -> TodayMood(Pose.LIMIT, "오늘 한도를 다 쓴 앱이 있어요.")
    nearLimit -> TodayMood(Pose.WARNING, "곧 한도예요. 조금만 아껴 써요!")
    paused -> TodayMood(Pose.REST, "잠깐 쉬는 중이에요. 금방 다시 지켜 줄게요.")
    hour >= 23 || hour < 6 -> TodayMood(Pose.SLEEP, "늦었어요. 폰은 내려놓고 푹 자요.")
    !hasRules -> TodayMood(Pose.DEFAULT, "안녕하세요! 줄이고 싶은 앱부터 골라 봐요.")
    else -> TodayMood(Pose.SUCCESS, "아직 한도 안쪽이에요. 잘하고 있어요!")
}

/** Settings card: pick the companion character; the chosen one shows its poses below. */
@Composable
internal fun MascotPicker(selected: MascotKind, select: (MascotKind) -> Unit, pro: Boolean = true) {
    // Locked characters stay visible (with a PRO mark); tapping one explains instead of switching.
    var lockedTapped by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("캐릭터"), style = MaterialTheme.typography.titleLarge)
        Text(tr("오늘 화면, 차단 화면, 알림과 기록에서 함께할 친구를 골라 주세요."), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MascotKind.entries.forEach { kind ->
                val chosen = kind == selected
                val locked = !pro && kind != MascotKind.FREE && !chosen
                val tint = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .2f)
                Column(Modifier.clip(RoundedCornerShape(16.dp)).border(BorderStroke(if (chosen) 2.dp else 1.dp, tint), RoundedCornerShape(16.dp))
                    .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else MaterialTheme.colorScheme.surface)
                    .selectable(chosen, role = Role.RadioButton) { if (locked) lockedTapped = true else select(kind) }.padding(8.dp)
                    .testTag("mascot-pick-${kind.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(kind.art(Pose.DEFAULT)), contentDescription = null,
                        modifier = Modifier.size(64.dp).graphicsLayer { alpha = if (locked) .55f else 1f })
                    Text(tr(kind.title), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (locked) Text("PRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("mascot-locked-${kind.name.lowercase()}"))
                }
            }
        }
        if (lockedTapped) Text(tr("햄스터 외의 캐릭터는 프로에서 쓸 수 있어요."), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pose.entries.forEach { pose ->
                Image(painterResource(selected.art(pose)), contentDescription = null, modifier = Modifier.size(44.dp))
            }
        }
    }
}

/** Settings switch for [LocalReduceMotion]. */
@Composable
internal fun MotionToggle(on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = set).padding(top = 8.dp).testTag("reduce-motion"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("움직이는 효과 줄이기"), style = MaterialTheme.typography.titleSmall)
            Text(tr("움직이는 테마와 캐릭터 움직임을 멈춰 배터리를 아껴요."), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = on, onCheckedChange = null)
    }
}
