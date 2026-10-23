package com.chronolock.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.chronolock.domain.AdFreeProduct
import com.chronolock.domain.ProProduct
import com.chronolock.domain.StorePurchase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BillingStatus {
    /** Setup or product query still running; the UI should not show an error yet. */
    CONNECTING,
    /** Product loaded and purchasable. */
    READY,
    /** The Play Store service could not be reached (network down, service restarting); a retry may succeed. */
    OFFLINE,
    /** Play is missing, the app is not installed from Play, or the product is not set up. */
    UNAVAILABLE;

    companion object {
        /** Maps a failed [BillingResult.getResponseCode] to the status shown to the user. */
        fun fromFailure(responseCode: Int): BillingStatus = when (responseCode) {
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.NETWORK_ERROR -> OFFLINE
            else -> UNAVAILABLE
        }
    }
}

data class BillingState(val status: BillingStatus = BillingStatus.CONNECTING, val price: String? = null,
    val adFree: Boolean = false, val pending: Boolean = false,
    /** The Pro pass: owned, its price once loaded, and a purchase still being processed. */
    val pro: Boolean = false, val proPrice: String? = null, val proPending: Boolean = false) {
    val available get() = status == BillingStatus.READY
}

/**
 * Google Play Billing for the ad-removal subscription. Entitlement is checked on the device from Play's purchase cache;
 * server-side token verification is a follow-up once the backend exists.
 */
@Singleton
class BillingController @Inject constructor(@ApplicationContext private val context: Context) {
    // The last entitlement Play confirmed, so a subscriber never sees a banner while the purchase query is in flight.
    private val prefs = context.getSharedPreferences("billing", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(BillingState(adFree = prefs.getBoolean(KEY_AD_FREE, false), pro = prefs.getBoolean(KEY_PRO, false)))
    val state: StateFlow<BillingState> = mutable.asStateFlow()
    @Volatile private var product: ProductDetails? = null
    @Volatile private var proProduct: ProductDetails? = null
    @Volatile private var connecting = false

    private val client = BillingClient.newBuilder(context)
        // ITEM_ALREADY_OWNED: subscribed on another device; the purchase query picks the entitlement up.
        .setListener { result, _ -> if (result.ok || result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refreshPurchases() }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Connects on first call and retries after a failure; safe to call on every resume. */
    fun connect() {
        if (client.isReady) {
            if (product == null || proProduct == null) queryProduct()
            refreshPurchases()
            return
        }
        if (connecting) return
        connecting = true
        mutable.update { it.copy(status = BillingStatus.CONNECTING) }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (!result.ok) {
                    android.util.Log.w("ChronoLock", "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    mutable.update { it.copy(status = BillingStatus.fromFailure(result.responseCode)) }
                    return
                }
                queryProduct()
                refreshPurchases()
            }
            override fun onBillingServiceDisconnected() = Unit // enableAutoServiceReconnection retries on the next call.
        })
    }

    /** Returns false when the purchase sheet could not be opened. */
    fun purchase(activity: Activity): Boolean {
        val details = product ?: return false
        val offer = details.baseOffer()?.offerToken ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer).build())).build()
        return client.launchBillingFlow(activity, params).ok
    }

    /** Opens Play's sheet for the Pro pass; false when it is not loaded (not set up yet, or offline). */
    fun purchasePro(activity: Activity): Boolean {
        val details = proProduct ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())).build()
        return client.launchBillingFlow(activity, params).ok
    }

    fun manageSubscription(activity: Activity) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=${AdFreeProduct.ID}&package=${context.packageName}")
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(AdFreeProduct.ID).setProductType(BillingClient.ProductType.SUBS).build())).build()
        mutable.update { it.copy(status = BillingStatus.CONNECTING) }
        client.queryProductDetailsAsync(params) { result, details ->
            product = details.productDetailsList.firstOrNull().takeIf { result.ok }
            val price = product?.baseOffer()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            if (product == null) android.util.Log.w("ChronoLock", "Billing product query: ${result.responseCode} ${result.debugMessage}")
            val status = when {
                product != null -> BillingStatus.READY
                result.ok -> BillingStatus.UNAVAILABLE // Product not configured for this package.
                else -> BillingStatus.fromFailure(result.responseCode)
            }
            mutable.update { it.copy(status = status, price = price) }
        }
        val pro = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(ProProduct.ID).setProductType(BillingClient.ProductType.INAPP).build())).build()
        client.queryProductDetailsAsync(pro) { result, details ->
            proProduct = details.productDetailsList.firstOrNull().takeIf { result.ok }
            mutable.update { it.copy(proPrice = proProduct?.oneTimePurchaseOfferDetails?.formattedPrice) }
        }
    }

    fun refreshPurchases() {
        if (!client.isReady) return
        refreshPro()
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (!result.ok) return@queryPurchasesAsync
            val store = purchases.map { StorePurchase(it.products, it.purchaseState == Purchase.PurchaseState.PURCHASED, it.isAcknowledged, it.purchaseToken) }
            val adFree = AdFreeProduct.entitled(store)
            prefs.edit().putBoolean(KEY_AD_FREE, adFree).apply()
            mutable.update { it.copy(adFree = adFree,
                pending = purchases.any { p -> AdFreeProduct.ID in p.products && p.purchaseState == Purchase.PurchaseState.PENDING }) }
            AdFreeProduct.toAcknowledge(store).forEach { purchase ->
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()) { ack ->
                    // Unacknowledged purchases are refunded by Play after three days; the next refresh retries.
                    if (!ack.ok) android.util.Log.w("ChronoLock", "Billing acknowledge failed: ${ack.responseCode} ${ack.debugMessage}")
                }
            }
        }
    }

    private fun refreshPro() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (!result.ok) return@queryPurchasesAsync
            val store = purchases.map { StorePurchase(it.products, it.purchaseState == Purchase.PurchaseState.PURCHASED, it.isAcknowledged, it.purchaseToken) }
            val pro = ProProduct.entitled(store)
            prefs.edit { putBoolean(KEY_PRO, pro) }
            mutable.update { it.copy(pro = pro,
                proPending = purchases.any { p -> ProProduct.ID in p.products && p.purchaseState == Purchase.PurchaseState.PENDING }) }
            ProProduct.toAcknowledge(store).forEach { purchase ->
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()) { ack ->
                    if (!ack.ok) android.util.Log.w("ChronoLock", "Pro acknowledge failed: ${ack.responseCode} ${ack.debugMessage}")
                }
            }
        }
    }

    /** The base plan (no offer id) when the console also lists intro or promo offers, else the first offer. */
    private fun ProductDetails.baseOffer() = subscriptionOfferDetails?.let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() }

    private val BillingResult.ok get() = responseCode == BillingClient.BillingResponseCode.OK

    private companion object {
        const val KEY_AD_FREE = "ad_free"
        const val KEY_PRO = "pro"
    }
}
