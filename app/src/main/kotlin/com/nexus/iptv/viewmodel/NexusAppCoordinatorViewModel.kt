package com.nexus.iptv.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.iptv.core.common.performance.DeviceResourceBudgetOptimizer
import com.nexus.iptv.core.common.performance.MemoryBudgetProfile
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.DvrRecordingEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.database.entity.SourceEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import com.nexus.iptv.core.datastore.NexusPreferencesDataSource
import com.nexus.iptv.core.model.ChannelSummaryItem
import com.nexus.iptv.core.model.ColorAccentScheme
import com.nexus.iptv.core.model.ContinueWatchingItem
import com.nexus.iptv.core.model.DashboardUiState
import com.nexus.iptv.core.model.NexusDestination
import com.nexus.iptv.core.model.NexusUiPreferences
import com.nexus.iptv.core.model.ThemeMode
import com.nexus.iptv.core.player.contract.DecoderMode
import com.nexus.iptv.core.player.media3.Media3VideoPlayerEngine
import com.nexus.iptv.core.streamengine.engine.SmartStreamEngine
import com.nexus.iptv.feature.diagnostics.domain.InternetDiagnosticSnapshot
import com.nexus.iptv.feature.diagnostics.domain.NetworkDiagnosticsEngine
import com.nexus.iptv.feature.diagnostics.domain.SourceProbeDiagnosticResult
import com.nexus.iptv.feature.dvr.domain.CatchupAndTimeshiftEngine
import com.nexus.iptv.feature.dvr.domain.StreamTimeCapabilities
import com.nexus.iptv.feature.dvr.recorder.DvrStreamRecordingEngine
import com.nexus.iptv.feature.epg.reminder.EpgReminderScheduler
import com.nexus.iptv.feature.epg.repository.EpgChannelTimelineRow
import com.nexus.iptv.feature.epg.repository.EpgRepository
import com.nexus.iptv.feature.livetv.domain.LiveChannelUiModel
import com.nexus.iptv.feature.livetv.repository.LiveTvRepository
import com.nexus.iptv.feature.multiview.hardware.HardwareMultiviewInspector
import com.nexus.iptv.feature.multiview.hardware.MultiviewHardwareCapability
import com.nexus.iptv.feature.multiview.player.MultiviewPlayerPoolManager
import com.nexus.iptv.feature.sources.repository.IptvSourceRepository
import com.nexus.iptv.feature.vod.domain.VodDomainEngine
import com.nexus.iptv.feature.vod.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NexusAppCoordinatorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val nexusDao: NexusDao,
    private val preferencesDataSource: NexusPreferencesDataSource,
    val sourceRepository: IptvSourceRepository,
    val liveTvRepository: LiveTvRepository,
    val epgRepository: EpgRepository,
    val vodRepository: VodRepository,
    val dvrEngine: DvrStreamRecordingEngine,
    val playerEngine: Media3VideoPlayerEngine,
    val smartStreamEngine: SmartStreamEngine,
    val diagnosticsEngine: NetworkDiagnosticsEngine,
    val multiviewPool: MultiviewPlayerPoolManager
) : ViewModel() {

    val uiPreferences: StateFlow<NexusUiPreferences> = preferencesDataSource.uiPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NexusUiPreferences())

    private val _selectedDestination = MutableStateFlow(NexusDestination.DASHBOARD)
    val selectedDestination: StateFlow<NexusDestination> = _selectedDestination.asStateFlow()

    val sources: StateFlow<List<SourceEntity>> = sourceRepository.observeAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = liveTvRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("Все каналы"))

    val liveChannels: StateFlow<List<LiveChannelUiModel>> = liveTvRepository.observeLiveChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rawChannelEntities: StateFlow<List<ChannelEntity>> = nexusDao.observeVisibleChannels(null, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vodCatalog: StateFlow<List<VodItemEntity>> = vodRepository.observeVodCatalog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dvrRecordings: StateFlow<List<DvrRecordingEntity>> = dvrEngine.observeRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeChannel = MutableStateFlow<LiveChannelUiModel?>(null)
    val activeChannel: StateFlow<LiveChannelUiModel?> = _activeChannel.asStateFlow()

    private val _isFullscreenPlayback = MutableStateFlow(false)
    val isFullscreenPlayback: StateFlow<Boolean> = _isFullscreenPlayback.asStateFlow()

    private val _decoderMode = MutableStateFlow(DecoderMode.AUTO)
    val decoderMode: StateFlow<DecoderMode> = _decoderMode.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _internetDiagnostic = MutableStateFlow<InternetDiagnosticSnapshot?>(null)
    val internetDiagnostic: StateFlow<InternetDiagnosticSnapshot?> = _internetDiagnostic.asStateFlow()

    private val _isRunningSpeedtest = MutableStateFlow(false)
    val isRunningSpeedtest: StateFlow<Boolean> = _isRunningSpeedtest.asStateFlow()

    private val _sourceProbes = MutableStateFlow(emptyMap<Long, SourceProbeDiagnosticResult>())
    val sourceProbes = _sourceProbes.asStateFlow()

    private val _epgTimelineRows = MutableStateFlow<List<EpgChannelTimelineRow>>(emptyList())
    val epgTimelineRows: StateFlow<List<EpgChannelTimelineRow>> = _epgTimelineRows.asStateFlow()

    val memoryBudget: MemoryBudgetProfile = DeviceResourceBudgetOptimizer.resolveForContext(appContext)
    val multiviewCapability: MultiviewHardwareCapability = HardwareMultiviewInspector.inspectDevice(appContext)

    val dashboardState: StateFlow<DashboardUiState> = combine(
        sources,
        liveChannels,
        categories,
        vodCatalog
    ) { srcList, chList, catList, vodList ->
        val favorites = chList.filter { it.isFavorite }.take(12).map { it.toSummaryItem() }
        val recents = chList.filter { it.lastWatchedEpochMs > 0L }
            .sortedByDescending { it.lastWatchedEpochMs }
            .take(12)
            .map { it.toSummaryItem() }
        val epgLive = chList.filter { !it.currentProgramTitle.isNullOrBlank() }.take(12).map { it.toSummaryItem() }
        val continueVod = vodList.filter { it.lastPositionMs > 0L }.take(10).map { vod ->
            ContinueWatchingItem(
                id = vod.id,
                title = vod.title,
                subtitle = vod.categoryName,
                posterUrl = vod.posterUrl,
                progressFraction = VodDomainEngine.computeProgressFraction(vod.lastPositionMs, vod.durationMs),
                remainingMinutes = VodDomainEngine.computeRemainingMinutes(vod.lastPositionMs, vod.durationMs),
                isLiveChannel = false
            )
        }

        DashboardUiState(
            activeProfileName = "Основной профиль",
            continueWatching = continueVod,
            recentChannels = recents.ifEmpty { chList.take(8).map { it.toSummaryItem() } },
            favoriteChannels = favorites,
            popularCategories = catList.filter { it != "Все каналы" }.take(12),
            epgHighlights = epgLive,
            vodRecommendations = vodList.take(10).map { vod ->
                ContinueWatchingItem(
                    id = vod.id,
                    title = vod.title,
                    subtitle = "${vod.categoryName} • ★ ${"%.1f".format(vod.rating)}",
                    posterUrl = vod.posterUrl,
                    progressFraction = 0f,
                    remainingMinutes = 0,
                    isLiveChannel = false
                )
            },
            activeSourcesCount = srcList.count { it.isEnabled }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun navigateTo(destination: NexusDestination) {
        _selectedDestination.value = destination
        if (destination == NexusDestination.EPG_GUIDE) {
            refreshEpgGrid()
        }
    }

    fun playLiveChannel(channel: LiveChannelUiModel, enterFullscreen: Boolean = true) {
        _activeChannel.value = channel
        _isFullscreenPlayback.value = enterFullscreen
        _selectedDestination.value = NexusDestination.LIVE_TV

        viewModelScope.launch {
            liveTvRepository.recordChannelWatched(channel.id)
            preferencesDataSource.recordLastPlayedChannel(channel.id)
            val entity = nexusDao.getChannelById(channel.id) ?: return@launch
            smartStreamEngine.startSupervisedPlayback(entity, _decoderMode.value)
        }
    }

    fun playLiveChannelById(channelId: Long) {
        val found = liveChannels.value.firstOrNull { it.id == channelId } ?: return
        playLiveChannel(found, enterFullscreen = true)
    }

    fun toggleFullscreen(fullscreen: Boolean) {
        _isFullscreenPlayback.value = fullscreen
    }

    fun addRemoteM3u(name: String, url: String, epgUrl: String?) {
        viewModelScope.launch {
            _statusMessage.value = "Загрузка и потоковый импорт плейлиста «$name»..."
            when (val res = sourceRepository.addRemoteM3uSource(name, url, epgUrl)) {
                is NexusResult.Success -> {
                    _statusMessage.value = "Источник «${res.data.sourceName}» успешно добавлен: ${res.data.importedChannelsCount} каналов."
                    if (!res.data.discoveredEpgUrl.isNullOrBlank()) {
                        epgRepository.syncEpgForSource(res.data.sourceId, res.data.discoveredEpgUrl)
                    }
                }
                is NexusResult.Error -> _statusMessage.value = res.exception.message
            }
        }
    }

    fun addXtreamSource(name: String, serverUrl: String, user: String, pass: String) {
        viewModelScope.launch {
            _statusMessage.value = "Подключение к Xtream Codes API «$name»..."
            when (val res = sourceRepository.addXtreamCodesSource(name, serverUrl, user, pass)) {
                is NexusResult.Success -> {
                    _statusMessage.value = "Xtream «${res.data.sourceName}» подключён: ${res.data.importedChannelsCount} каналов."
                    epgRepository.syncEpgForSource(res.data.sourceId)
                }
                is NexusResult.Error -> _statusMessage.value = res.exception.message
            }
        }
    }

    fun syncSource(sourceId: Long) {
        viewModelScope.launch {
            _statusMessage.value = "Синхронизация источника #$sourceId..."
            when (val res = sourceRepository.syncSourceById(sourceId)) {
                is NexusResult.Success -> _statusMessage.value = "Обновлено: ${res.data.importedChannelsCount} каналов."
                is NexusResult.Error -> _statusMessage.value = res.exception.message
            }
        }
    }

    fun verifySource(source: SourceEntity) {
        viewModelScope.launch {
            val probe = diagnosticsEngine.checkIptvSourceAndEpg(source)
            _sourceProbes.update { it + (source.id to probe) }
            _statusMessage.value = probe.userFriendlyMessage
        }
    }

    fun deleteSource(sourceId: Long) {
        viewModelScope.launch {
            sourceRepository.deleteSource(sourceId)
            _statusMessage.value = "Источник удалён."
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            when (val res = sourceRepository.exportEncryptedBackup()) {
                is NexusResult.Success -> _statusMessage.value = "Зашифрованная резервная копия (AES-256) создана (${res.data.length} байт)."
                is NexusResult.Error -> _statusMessage.value = res.exception.message
            }
        }
    }

    fun refreshEpgGrid() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val windowStart = now - 2 * 3_600_000L
            val windowEnd = now + 6 * 3_600_000L
            _epgTimelineRows.value = epgRepository.loadTimelineGrid(rawChannelEntities.value.take(60), windowStart, windowEnd)
        }
    }

    fun syncAllEpg() {
        viewModelScope.launch {
            _statusMessage.value = "Асинхронная загрузка XMLTV EPG..."
            sources.value.forEach { src ->
                epgRepository.syncEpgForSource(src.id)
            }
            refreshEpgGrid()
            _statusMessage.value = "Телепрограмма EPG обновлена."
        }
    }

    fun toggleProgramReminder(channel: ChannelEntity, program: EpgProgramEntity) {
        viewModelScope.launch {
            val enable = !program.hasReminder
            epgRepository.toggleProgramReminder(program.id, enable)
            val scheduler = EpgReminderScheduler(appContext)
            if (enable) {
                scheduler.scheduleProgramReminder(program.id, channel.name, program.title, program.startEpochMs)
                _statusMessage.value = "Напоминание установлено: «${program.title}»"
            } else {
                scheduler.cancelProgramReminder(program.id)
                _statusMessage.value = "Напоминание отменено."
            }
            refreshEpgGrid()
        }
    }

    fun playCatchupProgram(channel: ChannelEntity, program: EpgProgramEntity) {
        val catchupUrl = CatchupAndTimeshiftEngine.buildCatchupUrl(
            channel = channel,
            programStartEpochMs = program.startEpochMs,
            programEndEpochMs = program.endEpochMs
        )
        if (catchupUrl == null) {
            _statusMessage.value = "Архив Catch-up недоступен для данной передачи или источника."
            return
        }
        playerEngine.prepareAndPlay(streamUrl = catchupUrl, decoderMode = _decoderMode.value)
        _isFullscreenPlayback.value = true
        _selectedDestination.value = NexusDestination.LIVE_TV
    }

    fun runInternetSpeedtest() {
        viewModelScope.launch {
            _isRunningSpeedtest.value = true
            _internetDiagnostic.value = diagnosticsEngine.runInternetAndSpeedDiagnostic()
            _isRunningSpeedtest.value = false
        }
    }

    fun evaluateActiveChannelDvrCapabilities(): StreamTimeCapabilities? {
        val activeId = _activeChannel.value?.id ?: return null
        val entity = rawChannelEntities.value.firstOrNull { it.id == activeId } ?: return null
        val dvrDir = File(appContext.filesDir, "dvr_recordings")
        return CatchupAndTimeshiftEngine.evaluateCapabilities(
            channel = entity,
            availableStorageBytes = dvrDir.usableSpace.takeIf { it > 0L } ?: appContext.filesDir.usableSpace,
            isStorageWritable = appContext.filesDir.canWrite()
        )
    }

    fun updateThemeMode(mode: ThemeMode) { viewModelScope.launch { preferencesDataSource.setThemeMode(mode) } }
    fun updateAccentScheme(scheme: ColorAccentScheme) { viewModelScope.launch { preferencesDataSource.setAccentScheme(scheme) } }

    private fun LiveChannelUiModel.toSummaryItem(): ChannelSummaryItem = ChannelSummaryItem(
        id = id, number = number, name = name, category = categoryName, logoUrl = logoUrl,
        currentProgramTitle = currentProgramTitle, nextProgramTitle = nextProgramTitle,
        progressFraction = currentProgramProgress, isFavorite = isFavorite,
        healthScore = healthScore, hasCatchup = hasCatchup
    )

    override fun onCleared() {
        super.onCleared()
        smartStreamEngine.stopSupervision()
        playerEngine.release()
        multiviewPool.releaseAllSlots()
    }
}
