package com.nexus.iptv.core.common.performance

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context

data class MemoryBudgetProfile(
    val totalRamMb: Long,
    val isLowMemoryTier: Boolean,
    val imageMemoryCacheBytes: Long,
    val imageDiskCacheBytes: Long,
    val pagingPageSize: Int,
    val epgRetentionPastHours: Int,
    val epgRetentionFutureHours: Int
)

object DeviceResourceBudgetOptimizer {
    fun resolveForContext(context: Context): MemoryBudgetProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = if (memInfo.totalMem > 0L) memInfo.totalMem / (1024L * 1024L) else 2048L
        val isLowRamFlag = am?.isLowRamDevice == true
        return computeBudgetProfile(totalRamMb = totalRamMb, isLowRamDeviceFlag = isLowRamFlag)
    }

    fun computeBudgetProfile(totalRamMb: Long, isLowRamDeviceFlag: Boolean): MemoryBudgetProfile {
        val mb = 1024L * 1024L
        return when {
            isLowRamDeviceFlag || totalRamMb <= 2150L -> MemoryBudgetProfile(
                totalRamMb = totalRamMb, isLowMemoryTier = true,
                imageMemoryCacheBytes = 24L * mb, imageDiskCacheBytes = 120L * mb,
                pagingPageSize = 40, epgRetentionPastHours = 24, epgRetentionFutureHours = 48
            )
            totalRamMb < 3800L -> MemoryBudgetProfile(
                totalRamMb = totalRamMb, isLowMemoryTier = false,
                imageMemoryCacheBytes = 48L * mb, imageDiskCacheBytes = 250L * mb,
                pagingPageSize = 70, epgRetentionPastHours = 48, epgRetentionFutureHours = 96
            )
            else -> MemoryBudgetProfile(
                totalRamMb = totalRamMb, isLowMemoryTier = false,
                imageMemoryCacheBytes = 96L * mb, imageDiskCacheBytes = 500L * mb,
                pagingPageSize = 100, epgRetentionPastHours = 72, epgRetentionFutureHours = 168
            )
        }
    }

    fun shouldEvictNonEssentialCaches(trimLevel: Int): Boolean {
        return trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || trimLevel == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
    }
}
