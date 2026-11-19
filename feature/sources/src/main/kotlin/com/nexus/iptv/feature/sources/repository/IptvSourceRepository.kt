package com.nexus.iptv.feature.sources.repository

import com.nexus.iptv.core.common.result.NexusException
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.common.result.runNexusCatching
import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.SourceEntity
import com.nexus.iptv.core.network.client.NexusStreamNetworkClient
import com.nexus.iptv.core.network.client.SourceAvailabilityReport
import com.nexus.iptv.core.security.CredentialVault
import com.nexus.iptv.feature.sources.backup.BackupSourceItem
import com.nexus.iptv.feature.sources.backup.NexusBackupPayload
import com.nexus.iptv.feature.sources.parser.M3uStreamingParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

data class SourceSyncSummary(
    val sourceId: Long,
    val sourceName: String,
    val importedChannelsCount: Int,
    val discoveredEpgUrl: String?
)

@Singleton
class IptvSourceRepository @Inject constructor(
    private val nexusDao: NexusDao,
    private val networkClient: NexusStreamNetworkClient,
    private val credentialVault: CredentialVault
) {
    private val m3uParser = M3uStreamingParser(batchSize = 500)
    private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun observeAllSources(): Flow<List<SourceEntity>> = nexusDao.observeSources()

    suspend fun addRemoteM3uSource(
        name: String,
        playlistUrl: String,
        customEpgUrl: String? = null,
        userAgent: String? = null,
        autoUpdateIntervalHours: Int = 12
    ): NexusResult<SourceSyncSummary> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val sourceId = nexusDao.insertSource(
                SourceEntity(
                    name = name.trim(),
                    sourceType = "M3U_REMOTE",
                    endpointUrl = playlistUrl.trim(),
                    epgUrl = customEpgUrl?.trim()?.takeIf { it.isNotEmpty() },
                    userAgent = userAgent?.trim()?.takeIf { it.isNotEmpty() },
                    autoUpdateIntervalHours = autoUpdateIntervalHours,
                    lastSyncStatus = "SYNCING"
                )
            )
            syncSourceById(sourceId).getOrNull()
                ?: throw NexusException.UnexpectedException("Сбой первичной синхронизации источника $name")
        }
    }

    suspend fun addXtreamCodesSource(
        name: String,
        serverUrl: String,
        username: String,
        plainPassword: String,
        autoUpdateIntervalHours: Int = 12
    ): NexusResult<SourceSyncSummary> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val encryptedPass = credentialVault.encryptSecret(plainPassword).getOrNull()
                ?: throw NexusException.SecurityVaultException("Не удалось зашифровать пароль Xtream Codes")

            val cleanBase = serverUrl.trim().trimEnd('/')
            val defaultXmltvUrl = "$cleanBase/xmltv.php?username=$username&password=$plainPassword"
            val encryptedEpgUrl = credentialVault.encryptSecret(defaultXmltvUrl).getOrNull()

            val sourceId = nexusDao.insertSource(
                SourceEntity(
                    name = name.trim(),
                    sourceType = "XTREAM_CODES",
                    endpointUrl = cleanBase,
                    username = username.trim(),
                    encryptedPassword = encryptedPass,
                    epgUrl = encryptedEpgUrl,
                    autoUpdateIntervalHours = autoUpdateIntervalHours,
                    lastSyncStatus = "SYNCING"
                )
            )
            syncSourceById(sourceId).getOrNull()
                ?: throw NexusException.UnexpectedException("Сбой синхронизации портала Xtream Codes")
        }
    }

    suspend fun importLocalM3uReader(
        name: String,
        uriString: String,
        readerProvider: () -> BufferedReader
    ): NexusResult<SourceSyncSummary> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val sourceId = nexusDao.insertSource(
                SourceEntity(
                    name = name.trim(),
                    sourceType = "M3U_LOCAL",
                    endpointUrl = uriString,
                    autoUpdateIntervalHours = 0,
                    lastSyncStatus = "SYNCING"
                )
            )

            nexusDao.deleteChannelsBySource(sourceId)
            var discoveredEpg: String? = null

            val totalChannels = readerProvider().use { reader ->
                m3uParser.parseStream(
                    sourceId = sourceId,
                    reader = reader,
                    onHeaderParsed = { header ->
                        discoveredEpg = header.discoveredEpgUrls.firstOrNull()
                    },
                    onBatchReady = { batch ->
                        nexusDao.insertChannelsBatch(batch)
                    }
                )
            }

            nexusDao.updateSourceSyncStatus(
                sourceId = sourceId,
                timestampMs = System.currentTimeMillis(),
                status = "OK ($totalChannels каналов)",
                discoveredEpgUrl = discoveredEpg
            )

            SourceSyncSummary(
                sourceId = sourceId,
                sourceName = name,
                importedChannelsCount = totalChannels,
                discoveredEpgUrl = discoveredEpg
            )
        }
    }

    suspend fun syncSourceById(sourceId: Long): NexusResult<SourceSyncSummary> = withContext(Dispatchers.IO) {
        val source = nexusDao.getSourceById(sourceId)
            ?: return@withContext NexusResult.Error(NexusException.UnexpectedException("Источник #$sourceId не найден"))

        val result = when (source.sourceType) {
            "M3U_REMOTE" -> syncRemoteM3uSource(source)
            "XTREAM_CODES" -> syncXtreamSource(source)
            else -> NexusResult.Error(NexusException.UnexpectedException("Локальный файл обновляется вручную"))
        }

        if (result is NexusResult.Error) {
            nexusDao.updateSourceSyncStatus(
                sourceId = source.id,
                timestampMs = System.currentTimeMillis(),
                status = "ERROR: ${result.exception.diagnosticCode}"
            )
        }
        result
    }

    private suspend fun syncRemoteM3uSource(source: SourceEntity): NexusResult<SourceSyncSummary> {
        var discoveredEpg: String? = null
        var isFirstBatch = true

        return networkClient.executeStreamingReader(
            url = source.endpointUrl,
            customUserAgent = source.userAgent
        ) { reader ->
            val count = m3uParser.parseStream(
                sourceId = source.id,
                reader = reader,
                onHeaderParsed = { header ->
                    discoveredEpg = header.discoveredEpgUrls.firstOrNull()
                },
                onBatchReady = { batch ->
                    if (isFirstBatch) {
                        nexusDao.deleteChannelsBySource(source.id)
                        isFirstBatch = false
                    }
                    nexusDao.insertChannelsBatch(batch)
                }
            )
            nexusDao.updateSourceSyncStatus(
                sourceId = source.id,
                timestampMs = System.currentTimeMillis(),
                status = "OK ($count каналов)",
                discoveredEpgUrl = discoveredEpg
            )
            SourceSyncSummary(
                sourceId = source.id,
                sourceName = source.name,
                importedChannelsCount = count,
                discoveredEpgUrl = discoveredEpg ?: source.epgUrl
            )
        }
    }

    private suspend fun syncXtreamSource(source: SourceEntity): NexusResult<SourceSyncSummary> = runNexusCatching {
        val username = source.username
            ?: throw NexusException.UnauthorizedSourceException(source.name)
        val encryptedPass = source.encryptedPassword
            ?: throw NexusException.UnauthorizedSourceException(source.name)
        val plainPassword = credentialVault.decryptSecret(encryptedPass).getOrNull()
            ?: throw NexusException.SecurityVaultException("Ошибка расшифровки пароля для ${source.name}")

        val authResult = networkClient.authenticateXtream(source.endpointUrl, username, plainPassword)
        if (authResult is NexusResult.Error) throw authResult.exception

        val categoriesMap = networkClient.fetchXtreamLiveCategories(source.endpointUrl, username, plainPassword)
            .getOrNull()
            .orEmpty()
            .associate { it.categoryId to it.categoryName }

        val liveStreams = networkClient.fetchXtreamLiveStreams(source.endpointUrl, username, plainPassword)
            .getOrNull()
            ?: throw NexusException.UnexpectedException("Не удалось получить список каналов Xtream Codes")

        val channelEntities = liveStreams.mapIndexed { index, dto ->
            val categoryName = categoriesMap[dto.categoryId] ?: "Xtream TV"
            val streamUrl = "${source.endpointUrl}/live/$username/$plainPassword/${dto.streamId}.ts"
            ChannelEntity(
                sourceId = source.id,
                channelNumber = if (dto.num > 0) dto.num else (index + 1),
                name = dto.name.ifBlank { "Канал ${dto.streamId}" },
                normalizedName = dto.name.lowercase(),
                categoryName = categoryName,
                logoUrl = dto.streamIcon?.takeIf { it.isNotBlank() },
                streamUrl = streamUrl,
                epgChannelId = dto.epgChannelId?.takeIf { it.isNotBlank() },
                catchupType = if (dto.tvArchive == 1) "XTREAM" else null,
                catchupDays = dto.tvArchiveDurationDays,
                unifiedGroupKey = m3uParser.buildUnifiedGroupKey(dto.name)
            )
        }

        nexusDao.replaceChannelsForSource(source.id, channelEntities)
        nexusDao.updateSourceSyncStatus(
            sourceId = source.id,
            timestampMs = System.currentTimeMillis(),
            status = "OK (${channelEntities.size} каналов)"
        )

        SourceSyncSummary(
            sourceId = source.id,
            sourceName = source.name,
            importedChannelsCount = channelEntities.size,
            discoveredEpgUrl = source.epgUrl
        )
    }

    suspend fun deleteSource(sourceId: Long) {
        nexusDao.deleteSourceById(sourceId)
    }

    suspend fun checkSourceAvailability(source: SourceEntity): NexusResult<SourceAvailabilityReport> {
        return networkClient.verifySourceReachability(
            url = source.endpointUrl,
            userAgent = source.userAgent
        )
    }

    suspend fun exportEncryptedBackup(): NexusResult<String> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val sources = nexusDao.getEnabledSources()
            val backupItems = sources.map { src ->
                val plainPass = src.encryptedPassword?.let { credentialVault.decryptSecret(it).getOrNull() }
                BackupSourceItem(
                    name = src.name,
                    sourceType = src.sourceType,
                    endpointUrl = src.endpointUrl,
                    username = src.username,
                    plainPasswordForVault = plainPass,
                    epgUrl = src.epgUrl,
                    epgTimeshiftMinutes = src.epgTimeshiftMinutes,
                    userAgent = src.userAgent,
                    autoUpdateIntervalHours = src.autoUpdateIntervalHours,
                    priorityOrder = src.priorityOrder
                )
            }
            val payload = NexusBackupPayload(
                createdAtEpochMs = System.currentTimeMillis(),
                sources = backupItems
            )
            val jsonRaw = backupJson.encodeToString(NexusBackupPayload.serializer(), payload)
            credentialVault.encryptSecret(jsonRaw).getOrNull()
                ?: throw NexusException.SecurityVaultException("Не удалось зашифровать резервную копию")
        }
    }

    suspend fun restoreEncryptedBackup(cipherBackupBase64: String): NexusResult<Int> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val jsonRaw = credentialVault.decryptSecret(cipherBackupBase64).getOrNull()
                ?: throw NexusException.SecurityVaultException("Неверный ключ или повреждённая резервная копия")
            val payload = backupJson.decodeFromString(NexusBackupPayload.serializer(), jsonRaw)

            payload.sources.forEach { item ->
                val reEncryptedPassword = item.plainPasswordForVault?.let {
                    credentialVault.encryptSecret(it).getOrNull()
                }
                nexusDao.insertSource(
                    SourceEntity(
                        name = item.name,
                        sourceType = item.sourceType,
                        endpointUrl = item.endpointUrl,
                        username = item.username,
                        encryptedPassword = reEncryptedPassword,
                        epgUrl = item.epgUrl,
                        epgTimeshiftMinutes = item.epgTimeshiftMinutes,
                        userAgent = item.userAgent,
                        autoUpdateIntervalHours = item.autoUpdateIntervalHours,
                        priorityOrder = item.priorityOrder,
                        lastSyncStatus = "RESTORED"
                    )
                )
            }
            payload.sources.size
        }
    }
}
