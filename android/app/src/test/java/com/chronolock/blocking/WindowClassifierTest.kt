package com.chronolock.blocking

import org.junit.Assert.assertEquals
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
}
