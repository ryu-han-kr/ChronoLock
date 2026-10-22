package com.chronolock.feature

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val icons = LruCache<String, ImageBitmap>(96)

/** Application icon first; if the package hides it, the launcher activity's icon; a bitmap of either, or null. */
private fun loadIcon(context: Context, packageName: String, px: Int): ImageBitmap? {
    val pm = context.packageManager
    val drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
        ?: runCatching { pm.getLaunchIntentForPackage(packageName)?.let { pm.resolveActivity(it, 0)?.loadIcon(pm) } }.getOrNull()
        ?: runCatching { pm.getApplicationInfo(packageName, 0).loadIcon(pm) }.getOrNull()
    return runCatching { drawable?.toBitmap(px, px)?.asImageBitmap() }.getOrNull()?.also { icons.put(packageName, it) }
}

/** The installed app's launcher icon, or its first letter on a tinted tile while loading or when the app is gone. */
@Composable
internal fun AppIcon(packageName: String, label: String, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState(icons.get(packageName), packageName, px) {
        if (value == null) value = withContext(Dispatchers.IO) { loadIcon(context, packageName, px) }
    }
    val shape = RoundedCornerShape(size * .28f)
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size).clip(shape)) } ?: Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), shape), contentAlignment = Alignment.Center) {
        Text(label.take(1), color = MaterialTheme.colorScheme.primary, fontSize = (size.value * .42f).sp, fontWeight = FontWeight.Bold)
    }
}
