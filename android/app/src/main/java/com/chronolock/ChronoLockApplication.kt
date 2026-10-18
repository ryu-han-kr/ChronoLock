package com.chronolock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChronoLockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.chronolock.data.HistoryJobService.schedule(this)
    }
}
