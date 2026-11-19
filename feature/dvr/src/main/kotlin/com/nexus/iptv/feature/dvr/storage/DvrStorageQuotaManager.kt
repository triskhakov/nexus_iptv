package com.nexus.iptv.feature.dvr.storage

import com.nexus.iptv.core.database.entity.DvrRecordingEntity

data class DvrRetentionPolicy(
    val maxStorageQuotaBytes: Long = 10L * 1024L * 1024L * 1024L, // 10 ГБ по умолчанию
    val maxRecordingAgeDays: Int = 14,                              // Хранить записи не дольше 14 дней
    val minFreeDeviceSpaceBytes: Long = 1024L * 1024L * 1024L       // Оставлять минимум 1 ГБ свободным на устройстве
)

/**
 * Менеджер управления хранилищем и автоматического удаления старых записей по правилам (Этап 9, п. 10 ТЗ).
 *
 * Гарантирует:
 * - Защищённые пользователем записи (`isPinned == true`) никогда не удаляются автоматически;
 * - Сначала удаляются записи старше `maxRecordingAgeDays`;
 * - Затем, если суммарный объём превышает `maxStorageQuotaBytes` или свободное место на диске ниже
 *   `minFreeDeviceSpaceBytes`, удаляются самые старые незакреплённые записи по принципу FIFO.
 */
object DvrStorageQuotaManager {

    fun selectRecordingsForAutoCleanup(
        completedRecordingsOldestFirst: List<DvrRecordingEntity>,
        policy: DvrRetentionPolicy,
        currentDeviceFreeBytes: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<DvrRecordingEntity> {
        val candidates = completedRecordingsOldestFirst
            .filter { !it.isPinned && it.status == "COMPLETED" }
            .sortedBy { it.scheduledStartEpochMs }

        if (candidates.isEmpty()) return emptyList()

        val toDelete = LinkedHashSet<DvrRecordingEntity>()
        val maxAgeMs = policy.maxRecordingAgeDays * 86_400_000L

        var currentTotalUsedBytes = completedRecordingsOldestFirst.sumOf { it.recordedBytes }
        var simulatedFreeBytes = currentDeviceFreeBytes

        // 1. Удаление записей, превысивших максимальный срок хранения
        for (rec in candidates) {
            if (policy.maxRecordingAgeDays > 0 && (nowEpochMs - rec.scheduledStartEpochMs) > maxAgeMs) {
                toDelete.add(rec)
                currentTotalUsedBytes = (currentTotalUsedBytes - rec.recordedBytes).coerceAtLeast(0L)
                simulatedFreeBytes += rec.recordedBytes
            }
        }

        // 2. Удаление самых старых записей при превышении квоты DVR или нехватке места на устройстве
        for (rec in candidates) {
            if (toDelete.contains(rec)) continue
            val quotaExceeded = currentTotalUsedBytes > policy.maxStorageQuotaBytes
            val deviceLowSpace = simulatedFreeBytes < policy.minFreeDeviceSpaceBytes

            if (quotaExceeded || deviceLowSpace) {
                toDelete.add(rec)
                currentTotalUsedBytes = (currentTotalUsedBytes - rec.recordedBytes).coerceAtLeast(0L)
                simulatedFreeBytes += rec.recordedBytes
            } else {
                break
            }
        }

        return toDelete.toList()
    }
}
