package com.chronolock.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorHealthTest {
    private val old = Any()
    private val new = Any()

    @Test fun oldInstanceDestroyedAfterNewConnects_staysConnected() {
        val health = MonitorHealth()
        health.connected(old)
        health.connected(new)
        health.disconnected(old)
        assertTrue(health.state.value.connected)
    }

    @Test fun currentInstanceDestroyed_disconnects() {
        val health = MonitorHealth()
        health.connected(new)
        health.disconnected(new)
        assertFalse(health.state.value.connected)
    }

    @Test fun staleError_isIgnored_butCurrentErrorShows() {
        val health = MonitorHealth()
        health.connected(new)
        health.error(old)
        assertTrue(health.state.value.connected)
        assertNull(health.state.value.error)
        health.error(new)
        assertFalse(health.state.value.connected)
        assertEquals("앱 감지가 중단되었습니다. 접근성 서비스를 껐다 켜 주세요.", health.state.value.error)
    }

    @Test fun errorBeforeAnyConnection_shows() {
        val health = MonitorHealth()
        health.error(old)
        assertFalse(health.state.value.connected)
        assertEquals("앱 감지가 중단되었습니다. 접근성 서비스를 껐다 켜 주세요.", health.state.value.error)
    }
}
