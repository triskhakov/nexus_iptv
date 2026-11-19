package com.nexus.iptv.feature.vod.repository

import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.SeriesEpisodeEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import com.nexus.iptv.feature.vod.domain.VodDomainEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodRepository @Inject constructor(
    private val nexusDao: NexusDao
) {
    fun observeVodCatalog(contentType: String? = null, category: String? = null): Flow<List<VodItemEntity>> {
        val dbCat = if (category == null || category == "Все категории") null else category
        return nexusDao.observeVodItems(contentType, dbCat)
    }

    fun observeVodCategories(contentType: String? = null): Flow<List<String>> =
        nexusDao.observeVodCategories(contentType)

    fun observeContinueWatchingVod(limit: Int = 20): Flow<List<VodItemEntity>> =
        nexusDao.observeContinueWatchingVod(limit)

    fun observeEpisodesForSeries(seriesVodId: Long): Flow<List<SeriesEpisodeEntity>> =
        nexusDao.observeSeriesEpisodes(seriesVodId)

    suspend fun toggleVodFavorite(vodId: Long, currentFavorite: Boolean) = withContext(Dispatchers.IO) {
        nexusDao.setVodFavorite(vodId, !currentFavorite)
    }

    /**
     * Автоматическое сохранение позиции воспроизведения фильма (п. 11 ТЗ).
     */
    suspend fun saveMoviePlaybackPosition(
        vodId: Long,
        currentPositionMs: Long,
        totalDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val normalizedPos = VodDomainEngine.computeSavedResumePosition(currentPositionMs, totalDurationMs)
        nexusDao.updateVodPlaybackProgress(
            vodId = vodId,
            positionMs = normalizedPos,
            durationMs = totalDurationMs.coerceAtLeast(0L),
            watchedEpochMs = System.currentTimeMillis()
        )
    }

    /**
     * Автоматическое сохранение позиции воспроизведения серии сериала (п. 11 ТЗ).
     */
    suspend fun saveEpisodePlaybackPosition(
        seriesVodId: Long,
        episodeId: Long,
        currentPositionMs: Long,
        totalDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val normalizedPos = VodDomainEngine.computeSavedResumePosition(currentPositionMs, totalDurationMs)
        val now = System.currentTimeMillis()
        nexusDao.updateEpisodePlaybackProgress(
            episodeId = episodeId,
            positionMs = normalizedPos,
            durationMs = totalDurationMs.coerceAtLeast(0L),
            watchedEpochMs = now
        )
        // Обновляем также карточку самого сериала для секции «Продолжить просмотр» на Главном экране
        nexusDao.updateVodPlaybackProgress(
            vodId = seriesVodId,
            positionMs = normalizedPos,
            durationMs = totalDurationMs.coerceAtLeast(0L),
            watchedEpochMs = now
        )
    }
}
