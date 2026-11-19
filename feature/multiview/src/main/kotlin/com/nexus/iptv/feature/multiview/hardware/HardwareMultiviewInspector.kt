package com.nexus.iptv.feature.multiview.hardware

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Результат анализа аппаратных возможностей устройства для режима Multiview (Этап 10, п. 12 и п. 18 ТЗ).
 *
 * Важное условие п. 12 ТЗ:
 * «Учитывай ограничения аппаратного декодирования, памяти и пропускной способности сети.»
 */
data class MultiviewHardwareCapability(
    val maxRecommendedWindows: Int,         // 1, 2 или 4 окна
    val maxHardwareAvcInstances: Int,
    val maxHardwareHevcInstances: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDevice: Boolean,
    val recommendationSummary: String
)

object HardwareMultiviewInspector {

    fun inspectDevice(context: Context): MultiviewHardwareCapability {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRamMb = if (memInfo.totalMem > 0L) memInfo.totalMem / (1024L * 1024L) else 2048L
        val availableRamMb = if (memInfo.availMem > 0L) memInfo.availMem / (1024L * 1024L) else 800L
        val isLowRam = activityManager?.isLowRamDevice == true || totalRamMb <= 2100L

        val (hwAvcInstances, hwHevcInstances) = queryHardwareCodecInstances()

        return evaluateCapabilityFromMetrics(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowRamDevice = isLowRam,
            maxHardwareAvcInstances = hwAvcInstances,
            maxHardwareHevcInstances = hwHevcInstances
        )
    }

    /**
     * Чистая детерминированная функция расчёта лимита окон на основе аппаратных декодеров и свободной ОЗУ.
     */
    fun evaluateCapabilityFromMetrics(
        totalRamMb: Long,
        availableRamMb: Long,
        isLowRamDevice: Boolean,
        maxHardwareAvcInstances: Int,
        maxHardwareHevcInstances: Int
    ): MultiviewHardwareCapability {
        val effectiveCodecLimit = maxOf(maxHardwareAvcInstances, maxHardwareHevcInstances).coerceAtLeast(1)

        val recommendedWindows = when {
            // Бюджетные приставки с <= 2 ГБ RAM или дефицитом свободной ОЗУ (< 450 МБ) или 1 аппаратным декодером
            isLowRamDevice || availableRamMb < 450L || effectiveCodecLimit < 2 -> 1
            // Устройства с 2–3 ГБ RAM или 2–3 аппаратными сессиями декодера -> безопасно до 2 окон
            totalRamMb < 3500L || availableRamMb < 950L || effectiveCodecLimit < 4 -> 2
            // Флагманские TV Box / смартфоны (>= 4 ГБ RAM и >= 4 аппаратных декодеров) -> полная сетка 4 окна
            else -> 4
        }

        val summary = when (recommendedWindows) {
            1 -> "Ограничение оборудования: доступно $availableRamMb МБ ОЗУ (Всего $totalRamMb МБ), аппаратных декодеров: $effectiveCodecLimit. Рекомендуется 1 поток."
            2 -> "Сбалансированный режим (до 2 окон): $availableRamMb МБ свободной ОЗУ, аппаратных сессий декодера: $effectiveCodecLimit."
            else -> "Максимальная производительность (до 4 окон): $totalRamMb МБ ОЗУ, $effectiveCodecLimit аппаратных декодеров."
        }

        return MultiviewHardwareCapability(
            maxRecommendedWindows = recommendedWindows,
            maxHardwareAvcInstances = maxHardwareAvcInstances,
            maxHardwareHevcInstances = maxHardwareHevcInstances,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowRamDevice = isLowRamDevice,
            recommendationSummary = summary
        )
    }

    private fun queryHardwareCodecInstances(): Pair<Int, Int> {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var maxAvc = 1
            var maxHevc = 1

            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                val isSoftware = info.name.startsWith("OMX.google.", ignoreCase = true) ||
                    info.name.startsWith("c2.android.", ignoreCase = true)
                if (isSoftware) continue

                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        val caps = info.getCapabilitiesForType(type)
                        val instances = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) caps.maxSupportedInstances else 2
                        if (instances > maxAvc) maxAvc = instances
                    } else if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                        val caps = info.getCapabilitiesForType(type)
                        val instances = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) caps.maxSupportedInstances else 2
                        if (instances > maxHevc) maxHevc = instances
                    }
                }
            }
            maxAvc to maxHevc
        } catch (_: Exception) {
            2 to 2
        }
    }
}
