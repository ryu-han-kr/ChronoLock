package com.chronolock.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowClassifierTest {
    private val transient = setOf("com.android.systemui", "com.google.android.permissioncontroller", "com.google.android.inputmethod.latin")
    private val safety = setOf("com.chronolock.app", "com.android.settings", "com.google.android.permissioncontroller", "com.android.launcher3")

    @Test fun keyboard_withLauncherEntry_isTransient() =
        assertEquals(WindowKind.TRANSIENT, classifyWindow("com.google.android.inputmethod.latin", transient, safety, launchable = true))
    @Test fun permissionPrompt_isTransient_notLeave() =
        assertEquals(WindowKind.TRANSIENT, classifyWindow("com.google.android.permissioncontroller", transient, safety, launchable = false))
    @Test fun launcher_isLeave() =
        assertEquals(WindowKind.LEAVE, classifyWindow("com.android.launcher3", transient, safety, launchable = true))
    @Test fun ordinaryApp_isApp() =
        assertEquals(WindowKind.APP, classifyWindow("com.kakao.talk", transient, safety, launchable = true))
    @Test fun serviceWithoutLauncher_isNone() =
        assertEquals(WindowKind.NONE, classifyWindow("com.google.android.gms", transient, safety, launchable = false))

    private val kind = { name: String -> classifyWindow(name, transient, safety, launchable = true) }

    @Test fun onlyLauncherOnScreen_meansLeft() =
        assertTrue(leftWithoutEvent("com.kakao.talk", listOf("com.android.launcher3"), kind))
    @Test fun observedAppBesideLauncher_staysObserved() =
        assertFalse(leftWithoutEvent("com.kakao.talk", listOf("com.android.launcher3", "com.kakao.talk"), kind))
    @Test fun anotherAppOnScreen_isNotLeaving() =
        assertFalse(leftWithoutEvent("com.kakao.talk", listOf("com.android.launcher3", "com.example.other"), kind))
    @Test fun unreadableOrMissingWindows_keepObservation() {
        assertFalse(leftWithoutEvent("com.kakao.talk", listOf("com.android.launcher3", null), kind))
        assertFalse(leftWithoutEvent("com.kakao.talk", emptyList(), kind))
    }
    @Test fun keyboardOrPrompt_isNotLeaving() =
        assertFalse(leftWithoutEvent("com.kakao.talk", listOf("com.google.android.permissioncontroller"), kind))
    @Test fun nothingObserved_hasNothingToLeave() =
        assertFalse(leftWithoutEvent(null, listOf("com.android.launcher3"), kind))
}
