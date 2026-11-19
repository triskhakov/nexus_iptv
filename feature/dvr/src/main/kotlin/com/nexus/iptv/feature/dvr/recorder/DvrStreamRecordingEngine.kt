package com.nexus.iptv.feature.dvr.recorder

import com.nexus.iptv.core.common.result.NexusException
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.common.result.runNexusCatching
import com.nexus.iptv.core.common.security.UrlRedactor
import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.DvrRecordingEntity
import com.nexus.iptv.core.player.url.StreamUrlHeaderInjector
import com.nexus.iptv.feature.dvr.storage.DvrRetentionPolicy
import com.nexus.iptv.feature.dvr.storage.DvrStorageQuotaManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Промышленный движок записи прямых потоков (DVR) и кольцевого дискового буфера (Live Timeshift) — Этап 9 (п. 10 ТЗ).
 */
@Singleton
class DvrStreamRecordingEngine @Inject constructor(
    private val nexusDao: NexusDao
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeRecordingJobs = ConcurrentHashMap<Long, Job>()

    private val recordingHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun observeRecordings(): Flow<List<DvrRecordingEntity>> = nexusDao.observeDvrRecordings()

    /**
     * Запуск немедленной или запланированной записи потока в локальный MPEG-TS файл (.ts).
     */
    suspend fun startImmediateRecording(
        channelId: Long,
        channelName: String,
        programTitle: String,
        rawStreamUrl: String,
        recordingsDirectory: File,
        durationMinutes: Int = 60,
        retentionPolicy: DvrRetentionPolicy = DvrRetentionPolicy()
    ): NexusResult<Long> = withContext(Dispatchers.IO) {
        runNexusCatching {
            if (!recordingsDirectory.exists() && !recordingsDirectory.mkdirs()) {
                throw NexusException.StorageInsufficientException(0L, 0L)
            }

            // 1. Автоочистка старых записей перед стартом новой записи
            enforceStorageRetention(recordingsDirectory, retentionPolicy)

            val freeBytes = recordingsDirectory.usableSpace
            if (freeBytes < 500L * 1024L * 1024L) {
                throw NexusException.StorageInsufficientException(
                    requiredBytes = 500L * 1024L * 1024L,
                    availableBytes = freeBytes
                )
            }

            val nowMs = System.currentTimeMillis()
            val endMs = nowMs + durationMinutes.coerceIn(1, 360) * 60_000L
            val safeFileName = "nexus_dvr_${channelId}_$nowMs.ts"
            val outputFile = File(recordingsDirectory, safeFileName)

            val recordingId = nexusDao.insertDvrRecording(
                DvrRecordingEntity(
                    channelId = channelId,
                    channelName = channelName,
                    programTitle = programTitle,
                    streamUrl = rawStreamUrl,
                    outputFilePath = outputFile.absolutePath,
                    scheduledStartEpochMs = nowMs,
                    scheduledEndEpochMs = endMs,
                    status = "RECORDING"
                )
            )

            val job = engineScope.launch {
                executeStreamDumpToFile(
                    recordingId = recordingId,
                    rawStreamUrl = rawStreamUrl,
                    outputFile = outputFile,
                    stopAtEpochMs = endMs
                )
            }
            activeRecordingJobs[recordingId] = job
            recordingId
        }
    }

    /**
     * Планирование будущей записи передачи из телегида EPG.
     */
    suspend fun scheduleFutureRecording(
        channelId: Long,
        channelName: String,
        programTitle: String,
        rawStreamUrl: String,
        recordingsDirectory: File,
        startEpochMs: Long,
        endEpochMs: Long
    ): NexusResult<Long> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val safeFileName = "nexus_dvr_${channelId}_$startEpochMs.ts"
            val outputFile = File(recordingsDirectory, safeFileName)
            nexusDao.insertDvrRecording(
                DvrRecordingEntity(
                    channelId = channelId,
                    channelName = channelName,
                    programTitle = programTitle,
                    streamUrl = rawStreamUrl,
                    outputFilePath = outputFile.absolutePath,
                    scheduledStartEpochMs = startEpochMs,
                    scheduledEndEpochMs = endEpochMs,
                    status = "SCHEDULED"
                )
            )
        }
    }

    private suspend fun executeStreamDumpToFile(
        recordingId: Long,
        rawStreamUrl: String,
        outputFile: File,
        stopAtEpochMs: Long
    ) {
        var totalBytesWritten = 0L
        try {
            val parsed = StreamUrlHeaderInjector.parse(rawStreamUrl)
            val requestBuilder = Request.Builder()
                .url(parsed.cleanUrl)
                .header("User-Agent", parsed.headers["User-Agent"] ?: "NEXUS-IPTV-DVR/1.0")
            parsed.headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            recordingHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    nexusDao.updateDvrRecordingStatus(
                        recordingId = recordingId,
                        status = "FAILED",
                        bytes = 0L,
                        failureReason = "HTTP ${response.code} (${UrlRedactor.redact(parsed.cleanUrl)})"
                    )
                    return
                }

                val bodyStream = response.body?.byteStream() ?: run {
                    nexusDao.updateDvrRecordingStatus(recordingId, "FAILED", 0L, "Пустой поток сервера")
                    return
                }

                val buffer = ByteArray(64 * 1024)
                var lastDbUpdateMs = System.currentTimeMillis()

                FileOutputStream(outputFile).use { fos ->
                    while (engineScope.isActive && System.currentTimeMillis() < stopAtEpochMs) {
                        val read = bodyStream.read(buffer)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        totalBytesWritten += read

                        val now = System.currentTimeMillis()
                        if (now - lastDbUpdateMs >= 3_000L) {
                            lastDbUpdateMs = now
                            nexusDao.updateDvrRecordingStatus(recordingId, "RECORDING", totalBytesWritten)
                        }
                    }
                    fos.flush()
                }
            }

            nexusDao.updateDvrRecordingStatus(
                recordingId = recordingId,
                status = "COMPLETED",
                bytes = totalBytesWritten
            )
        } catch (ce: CancellationException) {
            nexusDao.updateDvrRecordingStatus(
                recordingId = recordingId,
                status = if (totalBytesWritten > 0L) "COMPLETED" else "CANCELLED",
                bytes = totalBytesWritten
            )
            throw ce
        } catch (e: Exception) {
            nexusDao.updateDvrRecordingStatus(
                recordingId = recordingId,
                status = "FAILED",
                bytes = totalBytesWritten,
                failureReason = e.message ?: "Обрыв соединения при записи"
            )
        } finally {
            activeRecordingJobs.remove(recordingId)
        }
    }

    suspend fun stopRecording(recordingId: Long) {
        activeRecordingJobs.remove(recordingId)?.cancel()
    }

    suspend fun togglePinRecording(recording: DvrRecordingEntity) = withContext(Dispatchers.IO) {
        nexusDao.setDvrRecordingPinned(recording.id, !recording.isPinned)
    }

    suspend fun deleteRecordingAndFile(recording: DvrRecordingEntity) = withContext(Dispatchers.IO) {
        activeRecordingJobs.remove(recording.id)?.cancel()
        runCatching { File(recording.outputFilePath).delete() }
        nexusDao.deleteDvrRecordingById(recording.id)
    }

    suspend fun enforceStorageRetention(
        recordingsDirectory: File,
        policy: DvrRetentionPolicy
    ): Int = withContext(Dispatchers.IO) {
        val unpinnedCompleted = nexusDao.getCompletedUnpinnedRecordingsOldestFirst()
        val toCleanup = DvrStorageQuotaManager.selectRecordingsForAutoCleanup(
            completedRecordingsOldestFirst = unpinnedCompleted,
            policy = policy,
            currentDeviceFreeBytes = recordingsDirectory.usableSpace
        )
        toCleanup.forEach { rec ->
            runCatching { File(rec.outputFilePath).delete() }
            nexusDao.deleteDvrRecordingById(rec.id)
        }
        toCleanup.size
    }
}
