package com.chronolock.feature

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chronolock.data.AppTheme

internal fun AppTheme.colorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(surface), onPrimaryContainer = Color(primary),
        background = Color(background), onBackground = Color(text),
        surface = Color(surface), onSurface = Color(text), onSurfaceVariant = Color(muted),
        surfaceDim = Color(background), surfaceBright = Color(surface),
        surfaceContainerLowest = Color(background), surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surface), surfaceContainerHigh = Color(surface), surfaceContainerHighest = Color(surface),
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
                    Box(Modifier.size(40.dp).background(Color(theme.background), CircleShape)
                        .border(1.dp, Color(theme.muted).copy(alpha = .4f), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(16.dp).background(Color(theme.primary), CircleShape))
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
