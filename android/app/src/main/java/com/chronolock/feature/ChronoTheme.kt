package com.chronolock.feature

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import com.chronolock.data.ThemeEffect
import kotlin.math.roundToInt
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.chronolock.data.ThemeCatalog
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chronolock.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chronolock.data.AppTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TileMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Pretendard Std (OFL): one static file per weight, since resource fonts are cached per resId and cannot carry a weight axis. */
internal val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun type(size: Int, weight: FontWeight, lineHeight: Int, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = Pretendard, fontSize = size.sp, fontWeight = weight, lineHeight = lineHeight.sp, letterSpacing = letterSpacing.sp)

/**
 * App type scale. Roles, not sizes, are referenced from screens:
 * display/headline = numbers and page titles, title = card and row headings, body = copy, label = chips and meta text.
 */
internal val ChronoTypography = Typography(
    displaySmall = type(36, FontWeight.SemiBold, 44, -0.5f),   // Today's total usage
    headlineLarge = type(32, FontWeight.Bold, 40, -0.5f),      // Focus countdown, history total
    headlineMedium = type(30, FontWeight.SemiBold, 36, -0.4f), // Page titles, onboarding welcome
    headlineSmall = type(24, FontWeight.Bold, 32, -0.3f),      // Metric values
    titleLarge = type(20, FontWeight.Bold, 28, -0.2f),         // Card titles
    titleMedium = type(17, FontWeight.SemiBold, 24),           // App names, row headings
    titleSmall = type(15, FontWeight.SemiBold, 20),
    bodyLarge = type(16, FontWeight.Normal, 24),               // Default Text
    bodyMedium = type(14, FontWeight.Normal, 20),              // Secondary lines
    bodySmall = type(12, FontWeight.Normal, 16),               // Captions and hints
    labelLarge = type(14, FontWeight.Medium, 20),              // Buttons
    labelMedium = type(13, FontWeight.Medium, 16),             // Status chips
    labelSmall = type(11, FontWeight.Normal, 14),              // Package names, tiny meta
)

internal fun AppTheme.colorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    // Raised containers (dialogs, menus, chips) tint the card colour toward the text colour instead of sharing one flat tone.
    // The tint is capped where muted copy (dialog text) or the accent (dialog buttons) would fall below 4.5:1.
    fun raised(amount: Float): Color {
        var step = (amount * 100).roundToInt()
        while (step > 0) {
            val color = lerp(Color(surface), Color(text), step / 100f)
            if (contrastRatio(Color(muted), color) >= 4.5f && contrastRatio(Color(primary), color) >= 4.5f) return color
            step--
        }
        return Color(surface)
    }
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primary).copy(alpha = .16f).compositeOver(Color(surface)), onPrimaryContainer = Color(text),
        background = Color(background), onBackground = Color(text),
        surface = Color(surface), onSurface = Color(text), onSurfaceVariant = Color(muted),
        surfaceDim = Color(background), surfaceBright = raised(.06f),
        surfaceContainerLowest = Color(background), surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surface), surfaceContainerHigh = raised(.04f), surfaceContainerHighest = raised(.08f),
        secondary = Color(primary), onSecondary = Color(onPrimary),
        // Selected chips and the navigation indicator: the accent on its own 14% tint stays under 4.5:1, so labels use the text colour.
        secondaryContainer = selectedTint(), onSecondaryContainer = Color(text),
        surfaceVariant = Color(background), outline = outlineFor(Color(muted).copy(alpha = .6f).compositeOver(Color(surface)), Color(surface), Color(background), Color(text)),
        outlineVariant = Color(muted).copy(alpha = .2f), surfaceTint = Color(primary),
    )
}

/** A translucent accent tint of up to 14%, lowered until text keeps 4.5:1 over every page colour and the cards (mid-grey pages). */
private fun AppTheme.selectedTint(): Color {
    var percent = 14
    while (percent > 0) {
        val tint = Color(primary).copy(alpha = percent / 100f)
        if ((pageColors + surface).all { contrastRatio(Color(text), tint.compositeOver(Color(it))) >= 4.5f }) return tint
        percent--
    }
    return Color(primary).copy(alpha = 0f)
}

@Composable
internal fun ChronoTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) DisposableEffect(view, theme) {
        view.context.activity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !theme.isDark
                isAppearanceLightNavigationBars = !theme.isDark
            }
        }
        onDispose { }
    }
    MaterialTheme(colorScheme = theme.colorScheme(), typography = ChronoTypography, content = content)
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Flat colour or the top-to-bottom gradient a palette asks for; drawn behind every screen and the block overlay. */
internal fun AppTheme.backgroundBrush(): Brush =
    if (pageColors.size > 1) Brush.verticalGradient(pageColors.map { Color(it) }) else SolidColor(Color(background))

/**
 * The page behind a screen. A live palette's colours flow down the page, one cycle every 24 seconds; the phase is read
 * while drawing only, so the screen above is not recomposed. Tests and the system animation setting stop the motion.
 */
@Composable
internal fun Modifier.themeBackground(theme: AppTheme): Modifier {
    if (!theme.live || LocalReduceMotion.current) return background(theme.backgroundBrush())
    val colors = remember(theme) { theme.pageColors.map { Color(it) } }
    val phase by rememberInfiniteTransition(label = "theme-flow")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "theme-flow-phase")
    return drawBehind {
        // A mirrored gradient repeats every two page heights, so moving it by that much loops without a seam.
        val span = size.height.coerceAtLeast(1f)
        val shift = phase * 2 * span
        drawRect(Brush.linearGradient(colors, Offset(0f, shift - span), Offset(0f, shift), TileMode.Mirror))
    }
}

/** Settings card: the built-in palettes, the current pick when it comes from the catalog, and a tile that opens the gallery. */
@Composable
internal fun ThemePicker(selected: AppTheme, select: (AppTheme) -> Unit, openGallery: () -> Unit = {}, favorites: Set<String> = emptySet(),
    followSystem: Boolean = false, editingNight: Boolean = false, setFollowSystem: (Boolean) -> Unit = {}, editNight: (Boolean) -> Unit = {}) {
    // Pinned palettes come first, then the built-ins; the current one leads only when it is neither.
    val pinned = AppTheme.values().filter { it.name in favorites }
    val row = pinned + AppTheme.builtIn.filter { it !in pinned }
    CardBox {
        // The gallery entry sits beside the title so it is visible without scrolling the row.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("화면 테마"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = openGallery, modifier = Modifier.testTag("theme-more")) { Text("+ " + tr("더보기")) }
        }
        Text(tr("원하는 분위기를 선택하세요. 다음 실행에도 유지됩니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().toggleable(value = followSystem, role = Role.Switch, onValueChange = setFollowSystem).testTag("theme-follow"),
            verticalAlignment = Alignment.CenterVertically) {
            Text(tr("기기 다크 모드 따라가기"), Modifier.weight(1f))
            Switch(checked = followSystem, onCheckedChange = null)
        }
        if (followSystem) {
            Text(tr("다크 모드일 때는 밤 테마, 아닐 때는 낮 테마를 써요. 아래에서 고를 테마를 정하세요."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !editingNight, onClick = { editNight(false) }, label = { Text("☀ " + tr("낮 테마")) }, modifier = Modifier.testTag("theme-day"))
                FilterChip(selected = editingNight, onClick = { editNight(true) }, label = { Text("☾ " + tr("밤 테마")) }, modifier = Modifier.testTag("theme-night"))
            }
        }
        LazyRow(Modifier.selectableGroup().testTag("themes-${ThemeCatalog.BASIC}"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selected !in row) item(key = "current") { ThemeTile(selected, chosen = true, tag = "theme-current", fullName = true) {} }
            items(row, key = { it.name }) { theme -> ThemeTile(theme, theme == selected, pinned = theme in pinned, fullName = true) { select(theme) } }
        }
        Text(tr("선택: %1\$s", selected.label.map { tr(it) }.joinToString(" ")) +
            selected.description.takeIf { it.isNotBlank() }?.let { " · " + tr(it) }.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

/** Full-screen overlay listing every palette by family; the caller stacks it above the screen and it closes on back. */
@Composable
internal fun ThemeGallery(selected: AppTheme, select: (AppTheme) -> Unit, favorites: Set<String> = emptySet(),
    toggleFavorite: (AppTheme) -> Unit = {}, close: () -> Unit) {
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.fillMaxSize().themeBackground(selected).systemBarsPadding().testTag("theme-gallery")) {
                val pinnedThemes = AppTheme.values().filter { it.name in favorites }
                val families = listOfNotNull(pinnedThemes.takeIf { it.isNotEmpty() }?.let { "찜한 테마" to it }, ThemeCatalog.BASIC to AppTheme.builtIn) +
                    ThemeCatalog.sections
                // Grid positions of each family header: the custom card and the hint come first.
                val headerIndex = remember(families) {
                    var index = 2
                    families.associate { (family, themes) -> (family to index).also { index += 1 + themes.size } }
                }
                val grid = rememberLazyGridState()
                val scope = rememberCoroutineScope()
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("뒤로")) }
                    Text(tr("테마"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { select(AppTheme.values().filter { it != selected }.random()) }, modifier = Modifier.testTag("theme-random")) {
                        Text("🎲 " + tr("랜덤"))
                    }
                    Text(tr("테마 %1\$d개", AppTheme.values().size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                }
                // Jump straight to a family instead of scrolling past a hundred palettes.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { scope.launch { grid.animateScrollToItem(0) } }, label = { Text(tr("나만의 테마")) })
                    families.forEach { (family, _) ->
                        AssistChip(onClick = { scope.launch { grid.animateScrollToItem(headerIndex.getValue(family)) } }, label = { Text(tr(family)) },
                            modifier = Modifier.testTag("theme-jump-$family"))
                    }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), state = grid, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize().selectableGroup().testTag("theme-gallery-grid")) {
                    item(key = "custom", span = { GridItemSpan(maxLineSpan) }) { CustomThemeCard(selected, select) }
                    item(key = "favorites-hint", span = { GridItemSpan(maxLineSpan) }) {
                        Text(tr("♡를 누르면 찜한 테마가 설정의 테마 목록 앞쪽에 나옵니다."), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    families.forEach { (family, themes) ->
                        item(key = "family-$family", span = { GridItemSpan(maxLineSpan) }) {
                            Text(tr(family), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                        }
                        // A pinned palette appears twice; its key in the pinned section must differ from its family one.
                        gridItems(themes, key = { if (family == "찜한 테마") "pinned-${it.name}" else it.name }) { theme ->
                            ThemeTile(theme, theme == selected, tag = if (family == "찜한 테마") "theme-pinned-${theme.name}" else "theme-${theme.name}",
                                pinned = theme.name in favorites, togglePin = { toggleFavorite(theme) }, fullName = family == "찜한 테마") { select(theme) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Any colour 000000-FFFFFF, picked on a hue/saturation wheel with a brightness bar or typed as a code or R/G/B,
 * with effects of different [ThemeEffect.Group]s combined. The preview follows the finger while it drags.
 */
@Composable
private fun CustomThemeCard(selected: AppTheme, apply: (AppTheme) -> Unit) {
    val start = remember { ThemeCatalog.parseCustom(selected.name) }
    // Hue, saturation and brightness are kept apart so a black or grey pick keeps its place on the wheel.
    val startHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start?.first ?: 0x4F7CAC, it) } }
    var hue by rememberSaveable { mutableFloatStateOf(startHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(startHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(startHsv[2]) }
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF
    fun setRgb(value: Int) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(value, it) }
        // Grey and black have no hue (and black no saturation); keep the wheel where it was.
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        if (hsv[2] > 0f) saturation = hsv[1]
        brightness = hsv[2]
    }
    var effectNames by rememberSaveable { mutableStateOf((start?.second ?: setOf(ThemeEffect.PASTEL)).map { it.name }) }
    val effects = effectNames.map { ThemeEffect.valueOf(it) }.toSet()
    val preview = remember(rgb, effects) { ThemeCatalog.custom(rgb, effects) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)
        .testTag("theme-custom"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("나만의 테마"), style = MaterialTheme.typography.titleSmall)
        Text(tr("색상환을 눌러 끌거나 코드·R·G·B를 넣고, 종류가 다른 효과는 함께 골라 적용하세요."), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ColorWheel(hue, saturation, brightness, { h, s -> hue = h; saturation = s; if (brightness < .05f) brightness = 1f },
                // Not filled: a weighted child's width is a minimum that would override the cap on a tablet.
                Modifier.weight(1f, fill = false).widthIn(max = 240.dp).testTag("custom-wheel"))
            // The page preview sits beside the wheel so it stays visible under a dragging finger.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF000000L or rgb.toLong()))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)))
                ThemeTile(preview, chosen = preview == selected, tag = "custom-preview") { apply(preview) }
            }
        }
        Text(tr("밝기"), style = MaterialTheme.typography.labelLarge)
        BrightnessBar(hue, saturation, brightness, { brightness = it }, Modifier.fillMaxWidth().testTag("custom-brightness"))
        ColorFields(rgb, ::setRgb)
        Text(tr("효과"), style = MaterialTheme.typography.labelLarge)
        listOf("바탕" to ThemeEffect.Group.PAGE, "그라데이션" to ThemeEffect.Group.GRADIENT, "포인트" to ThemeEffect.Group.ACCENT,
            "움직임" to ThemeEffect.Group.MOTION).forEach { (title, group) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(title), Modifier.width(76.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeEffect.entries.filter { it.group == group }.forEach { option ->
                        FilterChip(selected = option in effects, onClick = { effectNames = ThemeEffect.toggle(effects, option).map { it.name } },
                            label = { Text(tr(option.word)) }, modifier = Modifier.testTag("effect-${option.name}"))
                    }
                }
            }
        }
        Button(onClick = { apply(preview) }, modifier = Modifier.align(Alignment.End).testTag("custom-apply")) { Text(tr("적용")) }
    }
}

/** Hue around the circle, saturation from the centre out; touching or dragging anywhere moves the picker there at once. */
@Composable
private fun ColorWheel(hue: Float, saturation: Float, brightness: Float, pick: (Float, Float) -> Unit, modifier: Modifier) {
    val onPick by rememberUpdatedState(pick)
    var touch by remember { mutableStateOf<Offset?>(null) }
    val rainbow = remember { listOf(0f, 60f, 120f, 180f, 240f, 300f, 0f).map { Color.hsv(it, 1f, 1f) } }
    val current = Color.hsv(hue, saturation, brightness)
    Canvas(modifier.aspectRatio(1f).pointerInput(Unit) {
        fun at(position: Offset) {
            val radius = size.width.coerceAtMost(size.height) / 2f
            val delta = position - Offset(size.width / 2f, size.height / 2f)
            val angle = (Math.toDegrees(atan2(delta.y, delta.x).toDouble()).toFloat() + 360f) % 360f
            onPick(angle, (delta.getDistance() / radius).coerceIn(0f, 1f))
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            touch = down.position
            at(down.position)
            drag(down.id) { change -> change.consume(); touch = change.position; at(change.position) }
            touch = null
        }
    }) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(rainbow, center), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius), radius)
        if (brightness < 1f) drawCircle(Color.Black.copy(alpha = 1f - brightness), radius)
        val angle = Math.toRadians(hue.toDouble())
        val marker = center + Offset((cos(angle) * saturation * radius).toFloat(), (sin(angle) * saturation * radius).toFloat())
        drawCircle(Color.Black.copy(alpha = .35f), 12.dp.toPx(), marker, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White, 11.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
        drawCircle(current, 9.5f.dp.toPx(), marker)
        // A loupe above the finger shows the colour the finger itself covers.
        if (touch != null) {
            val loupe = Offset(marker.x, (marker.y - 44.dp.toPx()).coerceAtLeast(-radius * .2f))
            drawCircle(Color.Black.copy(alpha = .35f), 23.dp.toPx(), loupe, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White, 22.dp.toPx(), loupe)
            drawCircle(current, 19.dp.toPx(), loupe)
        }
    }
}

/** Black to the wheel's colour; touching or dragging sets the brightness at once. */
@Composable
private fun BrightnessBar(hue: Float, saturation: Float, brightness: Float, change: (Float) -> Unit, modifier: Modifier) {
    val onChange by rememberUpdatedState(change)
    val full = Color.hsv(hue, saturation, 1f)
    Canvas(modifier.height(28.dp).pointerInput(Unit) {
        fun at(x: Float) = onChange((x / size.width).coerceIn(0f, 1f))
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            at(down.position.x)
            horizontalDrag(down.id) { change -> change.consume(); at(change.position.x) }
        }
    }) {
        val bar = size.height * .5f
        val top = (size.height - bar) / 2f
        drawRoundRect(Brush.horizontalGradient(listOf(Color.Black, full)), Offset(0f, top), Size(size.width, bar), CornerRadius(bar / 2f))
        val x = (brightness * size.width).coerceIn(size.height / 2f, size.width - size.height / 2f)
        val thumb = Offset(x, size.height / 2f)
        drawCircle(Color.Black.copy(alpha = .35f), size.height / 2f, thumb, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White, size.height / 2f - 1.dp.toPx(), thumb)
        drawCircle(Color.hsv(hue, saturation, brightness), size.height / 2f - 4.dp.toPx(), thumb)
    }
}

/** The colour as a code and as R, G and B numbers; typing a complete value moves the wheel. */
@Composable
private fun ColorFields(rgb: Int, set: (Int) -> Unit) {
    // Done closes the keyboard so the wheel and preview above are not pushed out of view.
    val focus = LocalFocusManager.current
    val done = KeyboardActions(onDone = { focus.clearFocus() })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Keyed on the colour so dragging the wheel rewrites the text, while a half-typed value stays as typed.
        var hex by remember(rgb) { mutableStateOf("%06X".format(rgb)) }
        OutlinedTextField(value = hex, onValueChange = { value ->
                hex = value.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (hex.length == 6) set(hex.toInt(16))
            }, modifier = Modifier.weight(1.6f).testTag("custom-hex"), label = { Text(tr("색상 코드")) }, prefix = { Text("#") },
            singleLine = true, isError = hex.length != 6, textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = done)
        listOf("R" to 16, "G" to 8, "B" to 0).forEach { (channel, shift) ->
            var text by remember(rgb) { mutableStateOf("${(rgb shr shift) and 255}") }
            OutlinedTextField(value = text, onValueChange = { value ->
                    text = value.filter { it.isDigit() }.take(3)
                    text.toIntOrNull()?.takeIf { it <= 255 }?.let { set((rgb and (0xFF shl shift).inv()) or (it shl shift)) }
                }, modifier = Modifier.weight(1f).testTag("custom-$channel"), label = { Text(channel) }, singleLine = true,
                isError = text.toIntOrNull()?.let { it > 255 } ?: true, textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = done)
        }
    }
}

/**
 * A miniature of the palette: page background (or gradient), a card, the accent and a line of text. Under a family
 * header the family word is left out; [fullName] keeps it (on up to two lines) where palettes of several families
 * sit together, so "민트" says which mint and a "Forest" palette is not taken for the built-in one.
 */
@Composable
private fun ThemeTile(theme: AppTheme, chosen: Boolean, tag: String = "theme-${theme.name}", pinned: Boolean = false,
    togglePin: (() -> Unit)? = null, fullName: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).testTag(tag).selectable(selected = chosen, role = Role.RadioButton, onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(64.dp).clip(shape).themeBackground(theme)
            .border(if (chosen) 2.5.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color(theme.muted).copy(alpha = .35f), shape)
            .padding(12.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(theme.surface)).padding(6.dp)) {
                Box(Modifier.size(12.dp).background(Color(theme.primary), CircleShape))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(.7f).height(4.dp)
                    .background(Color(theme.text).copy(alpha = .55f), RoundedCornerShape(2.dp)))
            }
            when {
                togglePin != null -> IconButton(onClick = togglePin, modifier = Modifier.align(Alignment.TopEnd).offset(10.dp, (-10).dp).size(32.dp)
                    .testTag("pin-${theme.name}")) {
                    Icon(if (pinned) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (pinned) tr("찜 해제") else tr("찜하기"), tint = Color(theme.primary), modifier = Modifier.size(18.dp))
                }
                pinned -> Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(theme.primary),
                    modifier = Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(14.dp))
            }
        }
        Text((if (fullName) theme.label else theme.shortLabel).map { tr(it) }.joinToString(" "), style = MaterialTheme.typography.labelMedium,
            maxLines = if (fullName) 2 else 1, textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis, color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Contrast ratio per WCAG 2.x. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + 0.05f) / (dark + 0.05f)
}

/**
 * Borders of outlined buttons and fields are the only cue that they are controls, so they need 3:1 against cards and
 * page background (WCAG non-text contrast). Moves [base] toward [text] until both are met.
 */
internal fun outlineFor(base: Color, surface: Color, background: Color, text: Color): Color {
    var step = 0
    var color = base
    while (step < 20 && minOf(contrastRatio(color, surface), contrastRatio(color, background)) < 3f) {
        step++
        color = lerp(base, text, step / 20f)
    }
    return color
}
