package com.nexus.iptv.feature.livetv.repository

import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.feature.livetv.domain.LiveChannelUiModel
import com.nexus.iptv.feature.livetv.domain.LiveTvZappingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvRepository @Inject constructor(
    private val nexusDao: NexusDao
) {
    fun observeCategories(): Flow<List<String>> =
        nexusDao.observePopularCategories(limit = 100).map { categories ->
            listOf("Все каналы") + categories
        }

    fun observeLiveChannels(
        sourceId: Long? = null,
        category: String? = null
    ): Flow<List<LiveChannelUiModel>> {
        val dbCategory = if (category == null || category == "Все каналы") null else category
        return nexusDao.observeVisibleChannels(sourceId, dbCategory).map { entities ->
            val nowMs = System.currentTimeMillis()
            entities.map { entity -> mapEntityToUiModel(entity, nowMs) }
        }
    }

    private suspend fun mapEntityToUiModel(entity: ChannelEntity, nowMs: Long): LiveChannelUiModel {
        val effectiveEpgId = entity.manualEpgChannelId ?: entity.epgChannelId
        val programs = if (!effectiveEpgId.isNullOrBlank()) {
            nexusDao.getCurrentAndNextPrograms(effectiveEpgId, nowMs)
        } else {
            emptyList()
        }
        val currentProg = programs.firstOrNull { nowMs in it.startEpochMs..it.endEpochMs } ?: programs.firstOrNull()
        val nextProg = if (programs.size > 1) programs[1] else null

        return LiveChannelUiModel(
            id = entity.id,
            sourceId = entity.sourceId,
            number = entity.channelNumber,
            name = entity.name,
            categoryName = entity.categoryName,
            logoUrl = entity.logoUrl,
            streamUrl = entity.streamUrl,
            epgChannelId = effectiveEpgId,
            currentProgramTitle = currentProg?.title,
            currentProgramDescription = currentProg?.description,
            nextProgramTitle = nextProg?.title,
            currentProgramProgress = LiveTvZappingEngine.computeProgramProgress(currentProg, nowMs),
            isFavorite = entity.isFavorite,
            isLocked = entity.isLocked,
            healthScore = entity.healthScore,
            hasCatchup = entity.catchupDays > 0 && !entity.catchupType.isNullOrBlank(),
            customSortOrder = entity.customSortOrder,
            lastWatchedEpochMs = entity.lastWatchedEpochMs
        )
    }

    suspend fun toggleFavorite(channelId: Long, currentFavorite: Boolean) = withContext(Dispatchers.IO) {
        nexusDao.setChannelFavorite(channelId, !currentFavorite)
    }

    suspend fun hideChannel(channelId: Long) = withContext(Dispatchers.IO) {
        nexusDao.setChannelHidden(channelId, true)
    }

    suspend fun toggleChannelLock(channelId: Long, currentLocked: Boolean) = withContext(Dispatchers.IO) {
        nexusDao.setChannelLocked(channelId, !currentLocked)
    }

    suspend fun updateChannelSortOrder(channelId: Long, newOrder: Int) = withContext(Dispatchers.IO) {
        nexusDao.setChannelCustomSortOrder(channelId, newOrder)
    }

    suspend fun recordChannelWatched(channelId: Long) = withContext(Dispatchers.IO) {
        nexusDao.recordChannelWatchTimestamp(channelId, System.currentTimeMillis())
    }

    suspend fun findChannelByNumber(channelNumber: Int): ChannelEntity? = withContext(Dispatchers.IO) {
        nexusDao.getChannelByNumber(channelNumber)
    }
}
