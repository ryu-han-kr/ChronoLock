package com.chronolock.ads

import android.app.Activity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.chronolock.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdState(val configured: Boolean = false, val canRequestAds: Boolean = false, val privacyOptionsRequired: Boolean = false)

/** Google sample unit; debug builds use it so development never requests live ads. */
private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"

@Singleton
class AdsController @Inject constructor() {
    /** Release builds show ads only with a real unit ID from local.properties or -P. */
    val bannerId: String = BuildConfig.ADMOB_BANNER_ID.ifBlank { if (BuildConfig.DEBUG && BuildConfig.TEST_ADS) TEST_BANNER_ID else "" }
    private val started = AtomicBoolean(false)
    private val mutable = MutableStateFlow(AdState(configured = bannerId.isNotBlank()))
    val state: StateFlow<AdState> = mutable.asStateFlow()

    /** Asks for consent where required (e.g. EEA/UK) before the SDK is initialized or any ad is requested. */
    fun gatherConsent(activity: Activity) {
        if (bannerId.isBlank()) return
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            // The activity may have been rotated away while the request was in flight; the next resume asks again.
            if (!activity.isDestroyed) UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { update(activity, consent) }
        }, { update(activity, consent) })
        update(activity, consent) // Consent from a previous launch allows ads before the update returns.
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { update(activity, UserMessagingPlatform.getConsentInformation(activity)) }
    }

    private fun update(activity: Activity, consent: ConsentInformation) {
        val allowed = consent.canRequestAds()
        if (allowed && started.compareAndSet(false, true)) MobileAds.initialize(activity.applicationContext) {}
        mutable.value = mutable.value.copy(canRequestAds = allowed,
            privacyOptionsRequired = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED)
    }
}

@Composable
fun BannerAd(unitId: String) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("banner-ad")) {
        val context = LocalContext.current
        val width = maxWidth.value.toInt()
        val view = remember(unitId, width) {
            AdView(context).apply {
                adUnitId = unitId
                // The "large" replacement is about twice as tall and would cover the today card; keep the standard anchored size.
                @Suppress("DEPRECATION")
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))
            }
        }
        // Request outside composition so a discarded composition never fires a billable ad request.
        LaunchedEffect(view) { view.loadAd(AdRequest.Builder().build()) }
        DisposableEffect(view) { onDispose { view.destroy() } }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth())
    }
}
