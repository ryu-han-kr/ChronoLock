package com.chronolock.feature

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
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
 * The chosen character (or [kind]) in [pose], breathing gently from its feet. Decorative unless the caller adds a
 * content description.
 */
@Composable
internal fun MascotArt(pose: Pose, modifier: Modifier = Modifier, size: Dp = 96.dp, kind: MascotKind = LocalMascot.current, wobble: Boolean = false) {
    if (LocalReduceMotion.current) {
        Image(painterResource(kind.art(pose)), contentDescription = null, modifier = modifier.size(size))
        return
    }
    val transition = rememberInfiniteTransition(label = "mascot")
    val breath by transition.animateFloat(1f, 1.035f,
        infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse), label = "breath")
    val sway by transition.animateFloat(-7f, 7f,
        infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "wobble")
    Image(painterResource(kind.art(pose)), contentDescription = null, modifier = modifier.size(size).graphicsLayer {
        scaleX = breath; scaleY = breath
        rotationZ = if (wobble) sway else 0f
        transformOrigin = TransformOrigin(.5f, .95f)
    })
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
internal fun MascotPicker(selected: MascotKind, select: (MascotKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("캐릭터"), style = MaterialTheme.typography.titleLarge)
        Text(tr("오늘 화면, 차단 화면, 알림과 기록에서 함께할 친구를 골라 주세요."), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MascotKind.entries.forEach { kind ->
                val chosen = kind == selected
                val tint = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .2f)
                Column(Modifier.clip(RoundedCornerShape(16.dp)).border(BorderStroke(if (chosen) 2.dp else 1.dp, tint), RoundedCornerShape(16.dp))
                    .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else MaterialTheme.colorScheme.surface)
                    .selectable(chosen, role = Role.RadioButton) { select(kind) }.padding(8.dp).testTag("mascot-pick-${kind.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(kind.art(Pose.DEFAULT)), contentDescription = null, modifier = Modifier.size(64.dp))
                    Text(tr(kind.title), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
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
