package com.chronolock.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Turning detection off is kept until seen; repeated turn-offs widen one gap and a lock during any of them sticks. */
class MonitorGapTest {
    private val preferences = Preferences(ApplicationProvider.getApplicationContext<Context>())

    @After fun clean() = runBlocking { preferences.dismissMonitorGap() }

    @Test fun offThenBack_isOneClosedGap() = runBlocking {
        preferences.dismissMonitorGap()
        preferences.recordMonitorBack(50)
        assertNull("coming back without a turn-off records nothing", preferences.monitorGap.first())
        preferences.recordMonitorOff(100, locked = false)
        assertEquals(MonitorGap(100, null, false), preferences.monitorGap.first())
        preferences.recordMonitorBack(200)
        preferences.recordMonitorBack(300)
        assertEquals(MonitorGap(100, 200, false), preferences.monitorGap.first())
    }

    @Test fun anotherTurnOffBeforeSeen_keepsTheStartAndTheLock() = runBlocking {
        preferences.dismissMonitorGap()
        preferences.recordMonitorOff(100, locked = true)
        preferences.recordMonitorBack(200)
        preferences.recordMonitorOff(300, locked = false)
        assertEquals(MonitorGap(100, null, true), preferences.monitorGap.first())
        preferences.dismissMonitorGap()
        assertNull(preferences.monitorGap.first())
    }
}
