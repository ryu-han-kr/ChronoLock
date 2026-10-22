package com.chronolock

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChronoLockApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        com.chronolock.data.HistoryJobService.schedule(this)
    }

    // The ads SDK uses WorkManager; keep its JobScheduler IDs away from HistoryJobService (301).
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder().setJobSchedulerJobIdRange(1_000, 100_000).build()
}
