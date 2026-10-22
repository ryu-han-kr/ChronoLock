package com.chronolock.domain

import org.junit.Assert.*
import org.junit.Test

class AccountTest {
    private val session = AuthSession("user", "a@example.com", "access", "refresh", expiresAtEpochSeconds = 10_000)

    @Test fun session_refreshesOneMinuteBeforeExpiry() {
        assertFalse(session.needsRefresh(9_939))
        assertTrue(session.needsRefresh(9_940))
        assertTrue(session.needsRefresh(20_000))
    }

    @Test fun banner_onlyOnTodayAndHistory() {
        assertTrue(AdPolicy.showBanner(0, configured = true, adFree = false, canRequestAds = true, focusActive = false))
        assertTrue(AdPolicy.showBanner(2, configured = true, adFree = false, canRequestAds = true, focusActive = false))
        assertFalse(AdPolicy.showBanner(1, configured = true, adFree = false, canRequestAds = true, focusActive = false))
        assertFalse(AdPolicy.showBanner(3, configured = true, adFree = false, canRequestAds = true, focusActive = false))
    }

    @Test fun banner_hiddenForAdFreeFocusMissingConsentOrConfig() {
        assertFalse(AdPolicy.showBanner(0, configured = true, adFree = true, canRequestAds = true, focusActive = false))
        assertFalse(AdPolicy.showBanner(0, configured = true, adFree = false, canRequestAds = true, focusActive = true))
        assertFalse(AdPolicy.showBanner(0, configured = true, adFree = false, canRequestAds = false, focusActive = false))
        assertFalse(AdPolicy.showBanner(0, configured = false, adFree = false, canRequestAds = true, focusActive = false))
    }
}
