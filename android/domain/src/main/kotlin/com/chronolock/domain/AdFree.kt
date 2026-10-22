package com.chronolock.domain

/** Store-neutral view of a Play purchase, so entitlement rules stay testable without Play services. */
data class StorePurchase(val products: List<String>, val purchased: Boolean, val acknowledged: Boolean, val token: String)

/** Monthly ad-removal subscription (990 KRW base plan in Play Console). */
object AdFreeProduct {
    const val ID = "ad_free_monthly"

    /** Pending purchases (e.g. awaiting bank transfer) do not remove ads until Play reports them as purchased. */
    fun entitled(purchases: List<StorePurchase>): Boolean = purchases.any { ID in it.products && it.purchased }

    /** Play refunds subscriptions that stay unacknowledged for three days. */
    fun toAcknowledge(purchases: List<StorePurchase>): List<StorePurchase> =
        purchases.filter { ID in it.products && it.purchased && !it.acknowledged }
}
