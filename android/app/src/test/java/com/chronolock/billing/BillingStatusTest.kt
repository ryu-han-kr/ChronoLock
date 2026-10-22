package com.chronolock.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingStatusTest {
    @Test fun networkFailures_areOffline() {
        for (code in listOf(BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.NETWORK_ERROR))
            assertEquals(BillingStatus.OFFLINE, BillingStatus.fromFailure(code))
    }
    @Test fun storeFailures_areUnavailable() {
        for (code in listOf(BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.ITEM_UNAVAILABLE, BillingResponseCode.DEVELOPER_ERROR, BillingResponseCode.ERROR))
            assertEquals(BillingStatus.UNAVAILABLE, BillingStatus.fromFailure(code))
    }
    @Test fun onlyReady_isAvailable() {
        assertTrue(BillingState(status = BillingStatus.READY).available)
        assertFalse(BillingState().available)
        assertEquals(BillingStatus.CONNECTING, BillingState().status)
    }
}
