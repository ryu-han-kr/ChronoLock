package com.chronolock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskQrTest {
    @Test fun onlyThisDevicesCode_stopsTheFocus() {
        val secret = DeskQr.newSecret(ByteArray(16) { it.toByte() })
        assertEquals(16, secret.length)
        assertTrue(DeskQr.matches(secret, DeskQr.payload(secret)))
        assertTrue(DeskQr.matches(secret, " " + DeskQr.payload(secret) + "\n"))
        assertFalse(DeskQr.matches(secret, DeskQr.payload(DeskQr.newSecret(ByteArray(16) { 7 }))))
        assertFalse(DeskQr.matches(secret, "https://example.com"))
        assertFalse(DeskQr.matches(secret, null))
        assertFalse(DeskQr.matches(null, DeskQr.payload(secret)))
    }

    @Test fun shortRandomness_isRefused() {
        assertThrows(IllegalArgumentException::class.java) { DeskQr.newSecret(ByteArray(8)) }
    }
}
