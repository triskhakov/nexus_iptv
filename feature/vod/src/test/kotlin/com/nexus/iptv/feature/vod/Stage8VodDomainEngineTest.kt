package com.nexus.iptv.feature.vod

import com.nexus.iptv.core.database.entity.VodItemEntity
import com.nexus.iptv.feature.vod.domain.VodContentFilter
import com.nexus.iptv.feature.vod.domain.VodDomainEngine
import com.nexus.iptv.feature.vod.domain.VodSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Stage8VodDomainEngineTest {

    @Test
    fun `parseSeriesEpisodeFromTitle extracts clean series title, season, episode and year`() {
        val parsedEn = VodDomainEngine.parseSeriesEpisodeFromTitle("The Expanse (2015) - S03E08 - It Reaches Out [4K]")
        assertNotNull(parsedEn)
        assertEquals("The Expanse", parsedEn!!.cleanSeriesTitle)
        assertEquals(3, parsedEn.seasonNumber)
        assertEquals(8, parsedEn.episodeNumber)
        assertEquals(2015, parsedEn.releaseYear)

        val parsedRu = VodDomainEngine.parseSeriesEpisodeFromTitle("Чернобыль Сезон 1 Серия 5")
        assertNotNull(parsedRu)
        assertEquals("Чернобыль", parsedRu!!.cleanSeriesTitle)
        assertEquals(1, parsedRu.seasonNumber)
        assertEquals(5, parsedRu.episodeNumber)
    }

    @Test
    fun `computeSavedResumePosition resets position when under 30s or over 93 percent finished`() {
        val twoHoursMs = 7_200_000L

        // 1. Просмотрено всего 15 секунд -> не сохраняем как незавершённый (возвращает 0L)
        assertEquals(0L, VodDomainEngine.computeSavedResumePosition(15_000L, twoHoursMs))

        // 2. Просмотрено 45 минут из 120 -> сохраняется точная позиция
        val midPos = 45 * 60_000L
        assertEquals(midPos, VodDomainEngine.computeSavedResumePosition(midPos, twoHoursMs))

        // 3. Просмотрено 95% (финальные титры) -> позиция сбрасывается в 0L, чтобы фильм считался просмотренным
        val creditsPos = (twoHoursMs * 0.95).toLong()
        assertEquals(0L, VodDomainEngine.computeSavedResumePosition(creditsPos, twoHoursMs))
    }

    @Test
    fun `filterAndSortVod filters Continue Watching and sorts by rating and release year`() {
        val items = listOf(
            VodItemEntity(
                id = 1L, sourceId = 1L, contentType = "MOVIE", title = "Интерстеллар",
                categoryName = "Фантастика", genre = "Sci-Fi, Драма", releaseYear = 2014,
                rating = 8.7f, lastPositionMs = 1_800_000L, durationMs = 10_000_000L, lastWatchedEpochMs = 5000L
            ),
            VodItemEntity(
                id = 2L, sourceId = 1L, contentType = "MOVIE", title = "Дюна: Часть вторая",
                categoryName = "Фантастика", genre = "Sci-Fi, Боевик", releaseYear = 2024,
                rating = 8.9f, lastPositionMs = 0L, durationMs = 9_600_000L, lastWatchedEpochMs = 0L
            ),
            VodItemEntity(
                id = 3L, sourceId = 1L, contentType = "SERIES", title = "Задача трёх тел",
                categoryName = "Сериалы", genre = "Sci-Fi", releaseYear = 2024,
                rating = 7.8f, lastPositionMs = 900_000L, durationMs = 3_600_000L, lastWatchedEpochMs = 8000L
            )
        )

        val continueList = VodDomainEngine.filterAndSortVod(
            items = items,
            contentFilter = VodContentFilter.CONTINUE_WATCHING,
            selectedCategory = null,
            selectedGenre = null,
            searchQuery = "",
            sortOrder = VodSortOrder.RECENTLY_WATCHED
        )
        assertEquals(2, continueList.size)
        assertEquals(3L, continueList[0].id) // Просмотрен позже всех (8000L)
        assertEquals(1L, continueList[1].id)

        val moviesByRating = VodDomainEngine.filterAndSortVod(
            items = items,
            contentFilter = VodContentFilter.MOVIES,
            selectedCategory = "Фантастика",
            selectedGenre = "Sci-Fi",
            searchQuery = "",
            sortOrder = VodSortOrder.RATING_DESC
        )
        assertEquals(listOf(2L, 1L), moviesByRating.map { it.id })
    }
}
