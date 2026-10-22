package com.chronolock.domain

import org.junit.Assert.*
import org.junit.Test

class AdFreeTest {
    private fun purchase(product: String = AdFreeProduct.ID, purchased: Boolean = true, acknowledged: Boolean = true) =
        StorePurchase(listOf(product), purchased, acknowledged, "token-$product-$purchased-$acknowledged")

    @Test fun activeSubscription_removesAds() {
        assertTrue(AdFreeProduct.entitled(listOf(purchase())))
        assertTrue(AdFreeProduct.entitled(listOf(purchase(acknowledged = false))))
    }

    @Test fun pendingOtherOrMissingPurchases_keepAds() {
        assertFalse(AdFreeProduct.entitled(emptyList()))
        assertFalse(AdFreeProduct.entitled(listOf(purchase(purchased = false))))
        assertFalse(AdFreeProduct.entitled(listOf(purchase(product = "other"))))
    }

    @Test fun onlyPurchasedUnacknowledgedSubscriptions_areAcknowledged() {
        val waiting = purchase(acknowledged = false)
        val result = AdFreeProduct.toAcknowledge(listOf(purchase(), waiting, purchase(purchased = false, acknowledged = false),
            purchase(product = "other", acknowledged = false)))
        assertEquals(listOf(waiting), result)
    }
}
