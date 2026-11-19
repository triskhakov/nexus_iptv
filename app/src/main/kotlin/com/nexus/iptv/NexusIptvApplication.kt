package com.nexus.iptv

import android.app.Application
import com.nexus.iptv.core.common.performance.DeviceResourceBudgetOptimizer
import com.nexus.iptv.feature.sources.worker.PlaylistAutoSyncWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NexusIptvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PlaylistAutoSyncWorker.schedulePeriodicSync(this, repeatIntervalHours = 6L)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (DeviceResourceBudgetOptimizer.shouldEvictNonEssentialCaches(level)) {
            Runtime.getRuntime().gc()
        }
    }
}
