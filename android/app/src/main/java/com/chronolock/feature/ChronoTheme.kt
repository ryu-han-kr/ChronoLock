package com.chronolock.feature

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    fun raised(amount: Float) = lerp(Color(surface), Color(text), amount)
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primary).copy(alpha = .16f).compositeOver(Color(surface)), onPrimaryContainer = Color(text),
        background = Color(background), onBackground = Color(text),
        surface = Color(surface), onSurface = Color(text), onSurfaceVariant = Color(muted),
        surfaceDim = Color(background), surfaceBright = raised(.06f),
        surfaceContainerLowest = Color(background), surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surface), surfaceContainerHigh = raised(.04f), surfaceContainerHighest = raised(.08f),
        secondary = Color(primary), onSecondary = Color(onPrimary),
        secondaryContainer = Color(primary).copy(alpha = .14f), onSecondaryContainer = Color(primary),
        surfaceVariant = Color(background), outline = outlineFor(Color(muted).copy(alpha = .6f).compositeOver(Color(surface)), Color(surface), Color(background), Color(text)),
        outlineVariant = Color(muted).copy(alpha = .2f), surfaceTint = Color(primary),
    )
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
    gradientEnd?.let { Brush.verticalGradient(listOf(Color(background), Color(it))) } ?: SolidColor(Color(background))

/** Settings card: the built-in palettes, the current pick when it comes from the catalog, and a tile that opens the gallery. */
@Composable
internal fun ThemePicker(selected: AppTheme, select: (AppTheme) -> Unit, openGallery: () -> Unit = {}, favorites: Set<String> = emptySet()) {
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
        LazyRow(Modifier.selectableGroup().testTag("themes-${ThemeCatalog.BASIC}"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selected !in row) item(key = "current") { ThemeTile(selected, chosen = true, tag = "theme-current") {} }
            items(row, key = { it.name }) { theme -> ThemeTile(theme, theme == selected, pinned = theme in pinned) { select(theme) } }
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
            Column(Modifier.fillMaxSize().background(selected.backgroundBrush()).systemBarsPadding().testTag("theme-gallery")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("뒤로")) }
                    Text(tr("테마"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(tr("테마 %1\$d개", AppTheme.values().size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize().selectableGroup().testTag("theme-gallery-grid")) {
                    item(key = "favorites-hint", span = { GridItemSpan(maxLineSpan) }) {
                        Text(tr("♡를 누르면 찜한 테마가 설정의 테마 목록 앞쪽에 나옵니다."), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val pinned = AppTheme.values().filter { it.name in favorites }
                    (listOfNotNull(pinned.takeIf { it.isNotEmpty() }?.let { "찜한 테마" to it }, ThemeCatalog.BASIC to AppTheme.builtIn) +
                        ThemeCatalog.sections).forEach { (family, themes) ->
                        item(key = "family-$family", span = { GridItemSpan(maxLineSpan) }) {
                            Text(tr(family), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                        }
                        // A pinned palette appears twice; its key in the pinned section must differ from its family one.
                        gridItems(themes, key = { if (family == "찜한 테마") "pinned-${it.name}" else it.name }) { theme ->
                            ThemeTile(theme, theme == selected, tag = if (family == "찜한 테마") "theme-pinned-${theme.name}" else "theme-${theme.name}",
                                pinned = theme.name in favorites, togglePin = { toggleFavorite(theme) }) { select(theme) }
                        }
                    }
                }
            }
        }
    }
}

/** A miniature of the palette: page background (or gradient), a card, the accent and a line of text. */
@Composable
private fun ThemeTile(theme: AppTheme, chosen: Boolean, tag: String = "theme-${theme.name}", pinned: Boolean = false,
    togglePin: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).testTag(tag).selectable(selected = chosen, role = Role.RadioButton, onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(64.dp).clip(shape).background(theme.backgroundBrush())
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
        Text(theme.shortLabel.map { tr(it) }.joinToString(" "), style = MaterialTheme.typography.labelMedium, maxLines = 1,
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
