package com.chronolock.update

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play's flexible in-app update: when Play offers a newer build on any track this person can install from, Play's own
 * dialog asks once per app start, the download runs in the background, and [onReady] fires when a restart can install it.
 * Builds not installed from Play (debug, side-loaded) never see an update, so every failure is ignored.
 */
class InAppUpdates(activity: ComponentActivity, private val onReady: () -> Unit) {
    private val manager = AppUpdateManagerFactory.create(activity)
    // Registered at construction, before the activity starts, as the result API requires.
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    private val listener = InstallStateUpdatedListener { if (it.installStatus() == InstallStatus.DOWNLOADED) onReady() }
    private var asked = false

    fun check() {
        manager.registerListener(listener)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> onReady()
                !asked && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    asked = true
                    runCatching { manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)) }
                        .onFailure { android.util.Log.w("ChronoLock", "Could not start the update flow", it) }
                }
            }
        }.addOnFailureListener { android.util.Log.i("ChronoLock", "Update check unavailable: ${it.message}") }
    }

    /** Restarts the app to install the downloaded build. */
    fun complete() { manager.completeUpdate() }

    fun stop() = manager.unregisterListener(listener)
}
