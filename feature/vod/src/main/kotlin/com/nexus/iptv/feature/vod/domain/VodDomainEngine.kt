package com.nexus.iptv.feature.vod.domain

import com.nexus.iptv.core.database.entity.VodItemEntity

enum class VodContentFilter(val dbType: String?, val titleRu: String) {
    ALL(null, "Все (Фильмы и Сериалы)"),
    MOVIES("MOVIE", "Фильмы"),
    SERIES("SERIES", "Сериалы"),
    CONTINUE_WATCHING("CONTINUE", "Продолжить просмотр"),
    FAVORITES("FAV", "★ Избранное")
}

enum class VodSortOrder(val titleRu: String) {
    RATING_DESC("По рейтингу"),
    TITLE_ASC("По названию (А–Я)"),
    YEAR_DESC("По году выхода (новые)"),
    RECENTLY_WATCHED("Недавно просмотренные")
}

data class ParsedSeriesTag(
    val cleanSeriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val releaseYear: Int?
)

/**
 * Доменный движок раздела Movies & Series — VOD (Этап 8, п. 11 ТЗ).
 *
 * Реализует:
 * 1. Автоматическое распознавание фильмов и сериалов (сезонов S01E02 / "Сезон 1 Серия 2" и года выпуска) из M3U и Xtream;
 * 2. Интеллектуальный расчёт сохранения позиции воспроизведения (Auto-Resume):
 *    - Если просмотрено менее 2% или менее 30 секунд — старт с начала (0 мс);
 *    - Если просмотрено >= 93% хронометража — фильм/серия считается полностью просмотренной, позиция сбрасывается в 0 мс;
 *    - Иначе сохраняется точная позиция для возврата к просмотру в 1 клик.
 * 3. Фильтрацию и сортировку по категориям, жанрам, рейтингу, году и истории.
 */
object VodDomainEngine {

    private val SXX_EXX_REGEX = Regex("(?i)(?:s|сезон\\s*)(\\d{1,2})[\\s._-]*(?:e|ep|серия\\s*)(\\d{1,3})")
    private val YEAR_REGEX = Regex("\\b(19\\d{2}|20[0-2]\\d)\\b")

    /**
     * Извлекает номер сезона, номер серии, чистый заголовок сериала и год выхода из названия записи.
     */
    fun parseSeriesEpisodeFromTitle(rawTitle: String): ParsedSeriesTag? {
        val match = SXX_EXX_REGEX.find(rawTitle) ?: return null
        val season = match.groupValues[1].toIntOrNull() ?: 1
        val episode = match.groupValues[2].toIntOrNull() ?: 1

        val year = YEAR_REGEX.find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        val prefixTitle = rawTitle.substring(0, match.range.first)
            .replace(YEAR_REGEX, "")
            .replace(Regex("\\(\\s*\\)|\\[\\s*\\]"), "")
            .trim()
            .replace(Regex("[\\(\\)\\[\\]._\\-\\s]+$"), "")
            .trim()
            .ifEmpty { rawTitle.trim() }

        return ParsedSeriesTag(
            cleanSeriesTitle = prefixTitle,
            seasonNumber = season,
            episodeNumber = episode,
            releaseYear = year
        )
    }

    fun extractReleaseYear(rawTitle: String): Int? {
        return YEAR_REGEX.find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Вычисляет нормализованную позицию сохранения (0L, если фильм только начат или уже досмотрен до финальных титров >= 93%).
     */
    fun computeSavedResumePosition(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L || positionMs <= 30_000L) return 0L
        val ratio = positionMs.toDouble() / durationMs.toDouble()
        return when {
            ratio < 0.02 -> 0L
            ratio >= 0.93 -> 0L // Фильм завершён -> сбрасываем прогресс для повторного просмотра
            else -> positionMs
        }
    }

    fun computeProgressFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L || positionMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun computeRemainingMinutes(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= positionMs) return 0
        return ((durationMs - positionMs) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun filterAndSortVod(
        items: List<VodItemEntity>,
        contentFilter: VodContentFilter,
        selectedCategory: String?,
        selectedGenre: String?,
        searchQuery: String,
        sortOrder: VodSortOrder
    ): List<VodItemEntity> {
        val q = searchQuery.trim().lowercase()

        val filtered = items.filter { item ->
            val matchesType = when (contentFilter) {
                VodContentFilter.ALL -> true
                VodContentFilter.MOVIES -> item.contentType == "MOVIE"
                VodContentFilter.SERIES -> item.contentType == "SERIES"
                VodContentFilter.CONTINUE_WATCHING -> item.lastPositionMs > 0L
                VodContentFilter.FAVORITES -> item.isFavorite
            }
            val matchesCat = selectedCategory.isNullOrBlank() ||
                selectedCategory == "Все категории" ||
                item.categoryName.equals(selectedCategory, ignoreCase = true)
            val matchesGenre = selectedGenre.isNullOrBlank() ||
                selectedGenre == "Все жанры" ||
                (item.genre?.contains(selectedGenre, ignoreCase = true) == true)
            val matchesSearch = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                (item.description?.lowercase()?.contains(q) == true) ||
                (item.genre?.lowercase()?.contains(q) == true)

            matchesType && matchesCat && matchesGenre && matchesSearch
        }

        return when (sortOrder) {
            VodSortOrder.RATING_DESC -> filtered.sortedWith(
                compareByDescending<VodItemEntity> { it.isFavorite }
                    .thenByDescending { it.rating }
                    .thenBy { it.title }
            )
            VodSortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            VodSortOrder.YEAR_DESC -> filtered.sortedWith(
                compareByDescending<VodItemEntity> { it.releaseYear ?: 0 }
                    .thenByDescending { it.rating }
            )
            VodSortOrder.RECENTLY_WATCHED -> filtered.sortedWith(
                compareByDescending<VodItemEntity> { it.lastWatchedEpochMs }
                    .thenByDescending { it.rating }
            )
        }
    }
}
