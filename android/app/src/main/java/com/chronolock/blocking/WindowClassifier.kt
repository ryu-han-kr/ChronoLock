package com.chronolock.blocking

/** What a window-state change from a package means for the usage session. */
enum class WindowKind {
    /** Keyboard, permission prompt, system UI: drawn over the current app, so its session continues. */
    TRANSIENT,
    /** Home, settings, dialer, this app: the user left the app; the session closes and nothing new is observed. */
    LEAVE,
    /** Another launchable app took the screen. */
    APP,
    /** A package without a launcher entry (service dialogs, toasts): no change. */
    NONE,
}

/**
 * [transient] wins over [safety] because the permission controller and system UI are in both: they never mean
 * that the user left the app, while the launcher or settings do.
 */
internal fun classifyWindow(packageName: String, transient: Set<String>, safety: Set<String>, launchable: Boolean): WindowKind = when {
    packageName in transient -> WindowKind.TRANSIENT
    packageName in safety -> WindowKind.LEAVE
    launchable -> WindowKind.APP
    else -> WindowKind.NONE
}

/**
 * Whether the application windows on screen ([windows], their packages or null when unreadable) show that the user left
 * [observed] without its window event arriving: every one is home, settings or this app. An unreadable window, a
 * keyboard or prompt, or any other app on screen keeps the observation.
 */
internal fun leftWithoutEvent(observed: String?, windows: List<String?>, kind: (String) -> WindowKind): Boolean =
    observed != null && windows.isNotEmpty() && windows.all { it != null && it != observed && kind(it) == WindowKind.LEAVE }
