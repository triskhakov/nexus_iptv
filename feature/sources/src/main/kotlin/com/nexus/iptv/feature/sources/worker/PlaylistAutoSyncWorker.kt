package com.nexus.iptv.feature.sources.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.feature.sources.repository.IptvSourceRepository
import java.util.concurrent.TimeUnit

class PlaylistAutoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val nexusDao: NexusDao,
    private val sourceRepository: IptvSourceRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val nowMs = System.currentTimeMillis()
        val enabledSources = nexusDao.getEnabledSources()

        var anyTransientError = false
        for (source in enabledSources) {
            if (source.autoUpdateIntervalHours <= 0) continue
            val intervalMs = source.autoUpdateIntervalHours * 3_600_000L
            val isDue = (nowMs - source.lastUpdatedEpochMs) >= intervalMs

            if (isDue) {
                val syncResult = sourceRepository.syncSourceById(source.id)
                if (syncResult.isError) {
                    anyTransientError = true
                }
            }
        }
        return if (anyTransientError && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nexus_iptv_periodic_playlist_sync"

        fun schedulePeriodicSync(context: Context, repeatIntervalHours: Long = 6L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<PlaylistAutoSyncWorker>(
                repeatIntervalHours.coerceAtLeast(1L),
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
