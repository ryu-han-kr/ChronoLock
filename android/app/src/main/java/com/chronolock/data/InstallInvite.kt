package com.chronolock.data

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** The referrer text of the Play link this install came from, or null when Play has none or cannot answer in time. */
object InstallInvite {
    suspend fun referrer(context: Context): String? = withTimeoutOrNull(5_000) {
        val client = InstallReferrerClient.newBuilder(context).build()
        try {
            suspendCancellableCoroutine { done ->
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(code: Int) {
                        val text = if (code == InstallReferrerClient.InstallReferrerResponse.OK)
                            runCatching { client.installReferrer.installReferrer }.getOrNull() else null
                        if (done.isActive) done.resume(text)
                    }
                    override fun onInstallReferrerServiceDisconnected() { if (done.isActive) done.resume(null) }
                })
            }
        } finally { runCatching { client.endConnection() } }
    }
}
