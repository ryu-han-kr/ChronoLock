package com.chronolock.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.chronolock.domain.StorePurchase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** [available] is false when Play is missing, the app is not installed from Play, or the product is not set up. */
data class BillingState(val available: Boolean = false, val price: String? = null, val adFree: Boolean = false, val pending: Boolean = false)

/**
 * Google Play Billing for the ad-removal subscription. Entitlement is checked on the device from Play's purchase cache;
 * server-side token verification is a follow-up once the backend exists.
 */
@Singleton
class BillingController @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutable = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = mutable.asStateFlow()
    private var product: ProductDetails? = null

    private val client = BillingClient.newBuilder(context)
        .setListener { result, _ -> if (result.ok) refreshPurchases() }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (client.isReady) { refreshPurchases(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (!result.ok) return
                queryProduct()
                refreshPurchases()
            }
            override fun onBillingServiceDisconnected() = Unit // enableAutoServiceReconnection retries on the next call.
        })
    }

    /** Returns false when the purchase sheet could not be opened. */
    fun purchase(activity: Activity): Boolean {
        val details = product ?: return false
        val offer = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer).build())).build()
        return client.launchBillingFlow(activity, params).ok
    }

    fun manageSubscription(activity: Activity) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=${AdFreeProduct.ID}&package=${context.packageName}")
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(AdFreeProduct.ID).setProductType(BillingClient.ProductType.SUBS).build())).build()
        client.queryProductDetailsAsync(params) { result, details ->
            product = details.productDetailsList.firstOrNull().takeIf { result.ok }
            val price = product?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            mutable.update { it.copy(available = product != null, price = price) }
        }
    }

    fun refreshPurchases() {
        if (!client.isReady) return
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (!result.ok) return@queryPurchasesAsync
            val store = purchases.map { StorePurchase(it.products, it.purchaseState == Purchase.PurchaseState.PURCHASED, it.isAcknowledged, it.purchaseToken) }
            mutable.update { it.copy(adFree = AdFreeProduct.entitled(store),
                pending = purchases.any { p -> AdFreeProduct.ID in p.products && p.purchaseState == Purchase.PurchaseState.PENDING }) }
            AdFreeProduct.toAcknowledge(store).forEach { purchase ->
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()) {}
            }
        }
    }

    private val BillingResult.ok get() = responseCode == BillingClient.BillingResponseCode.OK
}
