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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chronolock.data.AppTheme

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
        surfaceVariant = Color(background), outline = Color(muted).copy(alpha = .6f),
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
    MaterialTheme(colorScheme = theme.colorScheme(), content = content)
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
internal fun ThemePicker(selected: AppTheme, select: (AppTheme) -> Unit) {
    CardBox {
        Text(tr("화면 테마"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(tr("원하는 분위기를 선택하세요. 다음 실행에도 유지됩니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTheme.values().forEach { theme ->
                val chosen = theme == selected
                Row(Modifier.fillMaxWidth()
                    .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp))
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { select(theme) })
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // A small swatch of the real palette: page, card and accent.
                    Row(Modifier.size(width = 52.dp, height = 36.dp).clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(theme.muted).copy(alpha = .35f), RoundedCornerShape(10.dp))) {
                        listOf(theme.background, theme.surface, theme.primary).forEach { swatch ->
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color(swatch)))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tr(theme.title), fontWeight = FontWeight.SemiBold)
                        Text(tr(theme.description), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    RadioButton(selected = chosen, onClick = null)
                }
            }
        }
    }
}
