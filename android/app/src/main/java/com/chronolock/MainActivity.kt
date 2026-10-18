package com.chronolock

import android.content.Intent
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
import com.chronolock.feature.ChronoApp
import com.chronolock.feature.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChronoApp(model, ::openPermissionSettings, ::signIn) }
    }
    private fun signIn() = lifecycleScope.launch {
        try { GoogleSignIn.request(this@MainActivity, AuthConfig.fromBuild().googleWebClientId)?.let(model::signIn) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            android.util.Log.w("ChronoLock", "Google sign-in failed", error)
            model.showError("Google 로그인을 시작하지 못했습니다. 기기에 Google 계정이 있는지 확인해 주세요.")
        }
    }
    override fun onResume() { super.onResume(); model.refresh() }
    private fun openPermissionSettings(accessibility: Boolean) {
        val intent = if (accessibility) Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) else
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(if (accessibility) Settings.ACTION_ACCESSIBILITY_SETTINGS else Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure { model.showError("설정 화면을 열 수 없습니다. Android 설정에서 권한을 직접 활성화해 주세요.") }
        }
    }
}
