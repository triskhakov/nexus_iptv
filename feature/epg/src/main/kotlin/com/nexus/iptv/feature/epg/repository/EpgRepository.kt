package com.nexus.iptv.feature.epg.repository

import com.nexus.iptv.core.common.result.NexusException
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.network.client.NexusStreamNetworkClient
import com.nexus.iptv.feature.epg.matcher.SmartEpgMatcher
import com.nexus.iptv.feature.epg.parser.XmltvChannelDescriptor
import com.nexus.iptv.feature.epg.parser.XmltvParseStats
import com.nexus.iptv.feature.epg.parser.XmltvStreamingParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

data class EpgChannelTimelineRow(
    val channel: ChannelEntity,
    val effectiveEpgId: String?,
    val programs: List<EpgProgramEntity>
)

@Singleton
class EpgRepository @Inject constructor(
    private val nexusDao: NexusDao,
    private val networkClient: NexusStreamNetworkClient
) {
    private val xmltvParser = XmltvStreamingParser(batchSize = 500)

    suspend fun syncEpgForSource(
        sourceId: Long,
        overrideEpgUrl: String? = null
    ): NexusResult<XmltvParseStats> = withContext(Dispatchers.IO) {
        val source = nexusDao.getSourceById(sourceId)
            ?: return@withContext NexusResult.Error(NexusException.UnexpectedException("Источник #$sourceId не найден"))

        val targetEpgUrl = overrideEpgUrl ?: source.epgUrl
        if (targetEpgUrl.isNullOrBlank()) {
            return@withContext NexusResult.Error(
                NexusException.XmltvMalformedException("Для источника «${source.name}» не задана ссылка XMLTV EPG")
            )
        }

        val discoveredChannels = ArrayList<XmltvChannelDescriptor>(512)
        val minEndMs = System.currentTimeMillis() - 24 * 3_600_000L
        var isFirstBatch = true

        val statsResult = networkClient.executeStreamingReader(
            url = targetEpgUrl,
            customUserAgent = source.userAgent
        ) { reader: BufferedReader ->
            xmltvParser.parseStream(
                sourceId = source.id,
                reader = reader,
                timeshiftMinutes = source.epgTimeshiftMinutes,
                minEndEpochMsFilter = minEndMs,
                onChannelDiscovered = { descriptor ->
                    discoveredChannels.add(descriptor)
                },
                onProgramBatchReady = { batch ->
                    if (isFirstBatch) {
                        nexusDao.deleteEpgBySource(source.id)
                        isFirstBatch = false
                    }
                    nexusDao.insertEpgBatch(batch)
                }
            )
        }

        if (statsResult is NexusResult.Success) {
            val sourceChannels = nexusDao.getChannelsBySource(source.id)
            val matchedMap = SmartEpgMatcher.matchChannelsToXmltv(sourceChannels, discoveredChannels)
            matchedMap.forEach { (channelId, matchedXmltvId) ->
                nexusDao.setChannelAutoMatchedEpgId(channelId, matchedXmltvId)
            }
        }

        statsResult
    }

    suspend fun setManualEpgMapping(channelId: Long, manualXmltvId: String?) = withContext(Dispatchers.IO) {
        nexusDao.setChannelManualEpgId(channelId, manualXmltvId?.trim()?.takeIf { it.isNotEmpty() })
    }

    suspend fun setSourceEpgTimeshiftMinutes(sourceId: Long, shiftMinutes: Int) = withContext(Dispatchers.IO) {
        nexusDao.updateSourceEpgTimeshift(sourceId, shiftMinutes.coerceIn(-720, 720))
    }

    suspend fun loadTimelineGrid(
        channels: List<ChannelEntity>,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<EpgChannelTimelineRow> = withContext(Dispatchers.IO) {
        channels.map { ch ->
            val epgId = ch.manualEpgChannelId ?: ch.epgChannelId
            val progs = if (!epgId.isNullOrBlank()) {
                nexusDao.getProgramsForChannelInWindow(epgId, windowStartMs, windowEndMs)
            } else {
                emptyList()
            }
            EpgChannelTimelineRow(
                channel = ch,
                effectiveEpgId = epgId,
                programs = progs
            )
        }
    }

    suspend fun searchProgramsByTitle(query: String): List<EpgProgramEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        nexusDao.searchEpgPrograms(query.trim(), System.currentTimeMillis())
    }

    suspend fun toggleProgramReminder(programId: Long, enable: Boolean) = withContext(Dispatchers.IO) {
        nexusDao.setProgramReminder(programId, enable)
    }
}
