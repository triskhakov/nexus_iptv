package com.nexus.iptv.feature.multiview.domain

import com.nexus.iptv.core.database.entity.ChannelEntity

/**
 * Режимы компоновки окон Multiview (Этап 10, п. 12 ТЗ):
 * - DUAL_SPLIT: 2 окна рядом (50% / 50%);
 * - DUAL_FOCUS: 2 окна с увеличенным основным окном (70% / 30%);
 * - QUAD_GRID: классическая сетка 2×2 на 4 окна (25% на каждое);
 * - QUAD_FOCUS: 1 большое главное окно (66% ширины) + 3 боковых окна (34% ширины).
 */
enum class MultiviewLayoutMode(val windowCount: Int, val titleRu: String) {
    DUAL_SPLIT(2, "2 окна (50 / 50)"),
    DUAL_FOCUS(2, "2 окна (Главное 70% + 30%)"),
    QUAD_GRID(4, "4 окна (Сетка 2×2)"),
    QUAD_FOCUS(4, "4 окна (Главное + 3 боковых)")
}

data class MultiviewSlotState(
    val slotIndex: Int,                 // 0..3
    val channel: ChannelEntity?,
    val isPrimaryWindow: Boolean,
    val isAudioActive: Boolean,
    val weightFraction: Float,          // Относительный размер окна в сетке
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Движок управления состоянием окон Multiview, эксклюзивным аудиофокусом (Solo Audio Focus)
 * и перераспределением размеров окон при смене главного экрана (Этап 10, п. 12 ТЗ).
 */
object MultiviewLayoutEngine {

    /**
     * Пересчитывает геометрические веса, статус главного окна и эксклюзивный звуковой канал
     * (звук воспроизводится строго из одного выбранного окна, остальные автоматически переходят в Mute).
     */
    fun computeSlotStates(
        layoutMode: MultiviewLayoutMode,
        assignedChannels: Map<Int, ChannelEntity?>,
        primarySlotIndex: Int,
        audioFocusSlotIndex: Int
    ): List<MultiviewSlotState> {
        val count = layoutMode.windowCount
        val safePrimary = primarySlotIndex.coerceIn(0, count - 1)
        val safeAudio = audioFocusSlotIndex.coerceIn(0, count - 1)

        return (0 until count).map { index ->
            val isPrimary = index == safePrimary
            val weight = when (layoutMode) {
                MultiviewLayoutMode.DUAL_SPLIT -> 0.50f
                MultiviewLayoutMode.DUAL_FOCUS -> if (isPrimary) 0.70f else 0.30f
                MultiviewLayoutMode.QUAD_GRID -> 0.25f
                MultiviewLayoutMode.QUAD_FOCUS -> if (isPrimary) 0.65f else 0.35f / 3f
            }

            MultiviewSlotState(
                slotIndex = index,
                channel = assignedChannels[index],
                isPrimaryWindow = isPrimary,
                isAudioActive = index == safeAudio && assignedChannels[index] != null,
                weightFraction = weight
            )
        }
    }

    /**
     * Рассчитывает рекомендуемую полосу пропускания сети (Мбит/с) для одновременного просмотра активных окон.
     */
    fun estimateRequiredBandwidthMbps(slots: List<MultiviewSlotState>): Float {
        val activeStreams = slots.count { it.channel != null }
        if (activeStreams == 0) return 0f
        // Основное окно может идти в FullHD (~8 Мбит/с), вспомогательные окна принудительно ограничиваются SD/720p (~3.5 Мбит/с)
        val primaryActive = slots.any { it.isPrimaryWindow && it.channel != null }
        val secondaryCount = (activeStreams - if (primaryActive) 1 else 0).coerceAtLeast(0)
        return (if (primaryActive) 8.0f else 0f) + (secondaryCount * 3.5f)
    }
}
