package com.chronolock.blocking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chronolock.R
import com.chronolock.domain.AppRule
import com.chronolock.domain.LockSetting
import org.junit.Assert.*
import org.junit.Test

class TodayWidgetStatusTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = 1_800_000_000_000L
    private val focusing = mapOf("test.app" to AppRule("test.app", "Test", dailyLimitSeconds = 600, focusUntilEpochMillis = now + 60_000))

    @Test fun lockComesBeforeFocusAndNothingShowsWithoutEither() {
        val goal = TodayWidget.status(LockSetting(goalDays = 7), focusing, now, context)
        assertEquals("🔒 " + context.getString(R.string.lock_pill_goal), goal)
        assertTrue(TodayWidget.status(LockSetting(untilEpochMillis = now + 3_600_000), focusing, now, context)!!.startsWith("🔒 "))
        val focus = TodayWidget.status(LockSetting(), focusing, now, context)!!
        assertFalse(focus.startsWith("🔒"))
        assertNull(TodayWidget.status(LockSetting(untilEpochMillis = now - 1), focusing, now + 60_000, context))
        assertNull(TodayWidget.status(LockSetting(), focusing.mapValues { it.value.copy(enabled = false) }, now, context))
    }
}
