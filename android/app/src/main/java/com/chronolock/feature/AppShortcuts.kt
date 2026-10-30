package com.chronolock.feature

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.chronolock.MainActivity
import com.chronolock.R

/** The launcher icon's long-press menu: start a focus, or open today's usage. Labels follow the app's language. */
object AppShortcuts {
    const val ACTION_FOCUS = "com.chronolock.action.FOCUS"
    const val ACTION_HISTORY = "com.chronolock.action.HISTORY"

    fun publish(context: Context, localized: Context) {
        fun shortcut(id: String, action: String, label: Int, icon: Int) = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(localized.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action))
            .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(
                shortcut("focus", ACTION_FOCUS, R.string.shortcut_focus, R.drawable.ic_tile_focus),
                shortcut("history", ACTION_HISTORY, R.string.shortcut_history, R.drawable.ic_chronolock),
            ))
        }.onFailure { android.util.Log.w("ChronoLock", "Shortcuts not published", it) }
    }
}
