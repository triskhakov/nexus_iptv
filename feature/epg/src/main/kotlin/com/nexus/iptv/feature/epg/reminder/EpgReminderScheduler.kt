package com.nexus.iptv.feature.epg.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgReminderScheduler @Inject constructor(
    private val context: Context
) {
    fun scheduleProgramReminder(
        programId: Long,
        channelName: String,
        programTitle: String,
        startEpochMs: Long
    ) {
        val nowMs = System.currentTimeMillis()
        val triggerAtMs = (startEpochMs - 60_000L).coerceAtLeast(nowMs + 1_000L)
        val delayMs = (triggerAtMs - nowMs).coerceAtLeast(0L)

        val inputData = Data.Builder()
            .putLong(EpgReminderWorker.KEY_PROGRAM_ID, programId)
            .putString(EpgReminderWorker.KEY_CHANNEL_NAME, channelName)
            .putString(EpgReminderWorker.KEY_PROGRAM_TITLE, programTitle)
            .build()

        val request = OneTimeWorkRequestBuilder<EpgReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(programId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelProgramReminder(programId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(programId))
    }

    private fun uniqueWorkName(programId: Long) = "nexus_epg_reminder_$programId"
}

class EpgReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val programId = inputData.getLong(KEY_PROGRAM_ID, 0L)
        val channelName = inputData.getString(KEY_CHANNEL_NAME) ?: "NEXUS IPTV"
        val programTitle = inputData.getString(KEY_PROGRAM_TITLE) ?: "Избранная передача"

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания телепрограммы EPG",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Скоро в эфире: $channelName")
            .setContentText("Через 1 минуту начинается «$programTitle»")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(programId.toInt(), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "nexus_epg_reminders_channel"
        const val KEY_PROGRAM_ID = "program_id"
        const val KEY_CHANNEL_NAME = "channel_name"
        const val KEY_PROGRAM_TITLE = "program_title"
    }
}
