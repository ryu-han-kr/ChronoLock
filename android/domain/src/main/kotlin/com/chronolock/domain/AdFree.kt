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

/** Monthly Pro subscription (990 KRW auto-renewing base plan in Play Console). */
object ProProduct {
    const val ID = "chronolock_pro_monthly"
    fun entitled(purchases: List<StorePurchase>): Boolean = purchases.any { ID in it.products && it.purchased }
    /** Play refunds subscriptions that stay unacknowledged for three days. */
    fun toAcknowledge(purchases: List<StorePurchase>): List<StorePurchase> = purchases.filter { ID in it.products && it.purchased && !it.acknowledged }
}

/** What the free tier allows; with Pro, or while the app does not gate features yet, none of these apply. */
object FreeTier {
    const val GROUPS = 2
    const val SITES = 5
    /** Longest strict-mode delay of the free version; 6 and 24 hours are Pro. */
    const val STRICT_MINUTES = 60
    const val HISTORY_DAYS = 30
}

/** Shared codes that open Pro without a purchase (testers, friends); only their hashes ship in the app. */
object ProCode {
    private val hashes = setOf("efc9c0cb6a8822a78b75ac2f3eb7bb38ee6dff4189d8b9c74358f7e21c3e81c2")

    /** Case, spaces and hyphens do not matter, so a code read aloud or retyped still works. */
    fun matches(input: String): Boolean {
        val normalized = input.uppercase().filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) return false
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest("chronolock-pro:$normalized".toByteArray())
        return digest.joinToString("") { "%02x".format(it) } in hashes
    }
}
