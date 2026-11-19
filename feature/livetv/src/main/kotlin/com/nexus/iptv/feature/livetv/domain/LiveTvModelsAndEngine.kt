package com.nexus.iptv.feature.livetv.domain

import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity

/**
 * Режимы отображения списка каналов в разделе Live TV (п. 6 ТЗ):
 * - LIST: подробный список с логотипом, текущей и следующей передачей, прогресс-баром;
 * - GRID: сеточный режим карточек;
 * - COMPACT: плотный список для быстрой навигации по тысячам каналов.
 */
enum class LiveTvViewMode(val titleRu: String) {
    LIST("Список"),
    GRID("Сетка"),
    COMPACT("Компактный")
}

/**
 * Критерии пользовательской сортировки каналов (п. 6 и п. 15 ТЗ).
 */
enum class ChannelSortMode(val titleRu: String) {
    CUSTOM_AND_NUMBER("По номеру / Пользовательская"),
    ALPHABETICAL("По алфавиту (А–Я)"),
    HEALTH_SCORE("По стабильности (Health Score)"),
    RECENTLY_WATCHED("По истории просмотра")
}

/**
 * Полная модель канала с привязанной текущей и следующей передачей EPG для UI.
 */
data class LiveChannelUiModel(
    val id: Long,
    val sourceId: Long,
    val number: Int,
    val name: String,
    val categoryName: String,
    val logoUrl: String?,
    val streamUrl: String,
    val epgChannelId: String?,
    val currentProgramTitle: String?,
    val currentProgramDescription: String?,
    val nextProgramTitle: String?,
    val currentProgramProgress: Float,
    val isFavorite: Boolean,
    val isLocked: Boolean,
    val healthScore: Int,
    val hasCatchup: Boolean,
    val customSortOrder: Int,
    val lastWatchedEpochMs: Long
)

/**
 * Движок навигации, фильтрации, цифрового набора номера с пульта ДУ и быстрого переключения (Quick Switch)
 * без выхода из полноэкранного режима (Этап 6, п. 6 и п. 19 ТЗ).
 */
object LiveTvZappingEngine {

    /**
     * Сортирует и фильтрует список каналов по выбранной категории, поисковому запросу и режиму сортировки.
     */
    fun filterAndSortChannels(
        channels: List<LiveChannelUiModel>,
        selectedCategory: String?,
        searchQuery: String,
        onlyFavorites: Boolean,
        sortMode: ChannelSortMode
    ): List<LiveChannelUiModel> {
        val cleanQuery = searchQuery.trim().lowercase()

        val filtered = channels.filter { ch ->
            val matchesFav = !onlyFavorites || ch.isFavorite
            val matchesCategory = selectedCategory.isNullOrBlank() ||
                selectedCategory == "Все каналы" ||
                ch.categoryName.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = cleanQuery.isEmpty() ||
                ch.name.lowercase().contains(cleanQuery) ||
                ch.number.toString() == cleanQuery ||
                (ch.currentProgramTitle?.lowercase()?.contains(cleanQuery) == true)
            matchesFav && matchesCategory && matchesSearch
        }

        return when (sortMode) {
            ChannelSortMode.CUSTOM_AND_NUMBER -> filtered.sortedWith(
                compareByDescending<LiveChannelUiModel> { it.isFavorite }
                    .thenBy { it.customSortOrder }
                    .thenBy { it.number }
            )
            ChannelSortMode.ALPHABETICAL -> filtered.sortedWith(
                compareByDescending<LiveChannelUiModel> { it.isFavorite }
                    .thenBy { it.name.lowercase() }
            )
            ChannelSortMode.HEALTH_SCORE -> filtered.sortedWith(
                compareByDescending<LiveChannelUiModel> { it.healthScore }
                    .thenBy { it.number }
            )
            ChannelSortMode.RECENTLY_WATCHED -> filtered.sortedWith(
                compareByDescending<LiveChannelUiModel> { it.lastWatchedEpochMs }
                    .thenBy { it.number }
            )
        }
    }

    /**
     * Вычисляет следующий или предыдущий канал при нажатии кнопок CH+ / CH- на пульте ДУ
     * с циклическим переходом (Wrap-Around) без выхода из полноэкранного режима.
     */
    fun resolveAdjacentChannel(
        orderedChannels: List<LiveChannelUiModel>,
        currentChannelId: Long,
        step: Int // +1 для CH+, -1 для CH-
    ): LiveChannelUiModel? {
        if (orderedChannels.isEmpty()) return null
        val currentIndex = orderedChannels.indexOfFirst { it.id == currentChannelId }
        if (currentIndex == -1) return orderedChannels.first()

        val size = orderedChannels.size
        val nextIndex = ((currentIndex + step) % size + size) % size
        return orderedChannels[nextIndex]
    }

    /**
     * Формирует очередь Quick Switch (последние просмотренные каналы без дублирования текущего активного канала).
     */
    fun buildQuickSwitchQueue(
        recentHistoryIds: List<Long>,
        allChannelsById: Map<Long, LiveChannelUiModel>,
        activeChannelId: Long?,
        maxItems: Int = 10
    ): List<LiveChannelUiModel> {
        return recentHistoryIds
            .asSequence()
            .distinct()
            .filter { it != activeChannelId }
            .mapNotNull { allChannelsById[it] }
            .take(maxItems)
            .toList()
    }

    /**
     * Вычисляет прогресс текущей телепередачи (0.0f..1.0f).
     */
    fun computeProgramProgress(program: EpgProgramEntity?, nowEpochMs: Long): Float {
        if (program == null) return 0f
        val duration = (program.endEpochMs - program.startEpochMs).coerceAtLeast(1L)
        val elapsed = (nowEpochMs - program.startEpochMs).coerceIn(0L, duration)
        return (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}

/**
 * Контроллер набора номера канала цифровыми клавишами пульта ДУ (0..9).
 * Накапливает до 4 цифр и автоматически переключает на канал с соответствующим номером.
 */
class NumericKeypadChannelBuffer(private val maxDigits: Int = 4) {
    private val digits = StringBuilder()

    fun appendDigit(digit: Int): Int {
        if (digit !in 0..9) return currentNumber() ?: 0
        if (digits.length >= maxDigits) {
            digits.clear()
        }
        digits.append(digit)
        return digits.toString().toIntOrNull() ?: 0
    }

    fun currentNumber(): Int? = digits.toString().toIntOrNull()

    fun clear() {
        digits.clear()
    }
}
