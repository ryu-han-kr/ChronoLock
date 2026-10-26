package com.chronolock

import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.chronolock.account.AuthConfig
import com.chronolock.account.GoogleSignIn
import com.chronolock.ads.AdsController
import com.chronolock.billing.BillingController
import com.chronolock.feature.ChronoApp
import com.chronolock.feature.MainViewModel
import com.chronolock.update.InAppUpdates
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    @Inject lateinit var ads: AdsController
    @Inject lateinit var billing: BillingController
    private val updates = InAppUpdates(this) { model.updateDownloaded() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ads.gatherConsent(this)
        billing.connect()
        setContent { ChronoApp(model, ::openPermissionSettings, ::signIn, { ads.showPrivacyOptions(this) },
            manageSubscription = { billing.manageSubscription(this) },
            installUpdate = updates::complete,
            buyPro = { if (!billing.purchasePro(this)) model.showError("결제 화면을 열지 못했습니다. 잠시 후 다시 시도해 주세요.") },
            managePro = { billing.managePro(this) }) }
        updates.check()
    }
    override fun onDestroy() { updates.stop(); super.onDestroy() }
    private fun signIn() = lifecycleScope.launch {
        try { GoogleSignIn.request(this@MainActivity, AuthConfig.fromBuild().googleWebClientId)?.let(model::signIn) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            android.util.Log.w("ChronoLock", "Google sign-in failed", error)
            model.showError("Google 로그인을 시작하지 못했습니다. 기기에 Google 계정이 있는지 확인해 주세요.")
        }
    }
    override fun onResume() { super.onResume(); model.refresh(); billing.connect() }
    private fun openPermissionSettings(accessibility: Boolean) {
        val intent = if (accessibility) Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            // Undocumented but widely honoured: Settings scrolls to and highlights this service instead of the full list.
            val service = ComponentName(this@MainActivity, com.chronolock.blocking.ChronoAccessibilityService::class.java).flattenToString()
            putExtra(":settings:fragment_args_key", service)
            putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", service) })
        } else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(if (accessibility) Settings.ACTION_ACCESSIBILITY_SETTINGS else Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure { model.showError("설정 화면을 열 수 없습니다. Android 설정에서 권한을 직접 활성화해 주세요.") }
        }
    }
}
