package com.nexus.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.nexus.iptv.core.database.entity.SeriesEpisodeEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import com.nexus.iptv.core.model.DeviceFormFactor
import com.nexus.iptv.core.model.NexusDestination
import com.nexus.iptv.core.model.NexusUiPreferences
import com.nexus.iptv.core.player.contract.AspectRatioMode
import com.nexus.iptv.core.player.contract.PlayerState
import com.nexus.iptv.core.player.external.ExternalPlayerLauncher
import com.nexus.iptv.core.player.ui.NexusVideoPlayerOverlay
import com.nexus.iptv.core.uimobile.dashboard.NexusMobileDashboardScreen
import com.nexus.iptv.core.uitv.dashboard.NexusTvDashboardScreen
import com.nexus.iptv.device.DeviceFormFactorDetector
import com.nexus.iptv.feature.diagnostics.ui.NetworkDiagnosticsScreen
import com.nexus.iptv.feature.dvr.storage.DvrRetentionPolicy
import com.nexus.iptv.feature.dvr.ui.DvrAndTimeshiftScreen
import com.nexus.iptv.feature.epg.ui.EpgGuideTimelineScreen
import com.nexus.iptv.feature.livetv.ui.LiveTvSectionScreen
import com.nexus.iptv.feature.multiview.ui.MultiviewSectionScreen
import com.nexus.iptv.feature.settings.security.CustomizableRemoteButton
import com.nexus.iptv.feature.settings.security.RemoteCustomAction
import com.nexus.iptv.feature.settings.security.UserProfileConfig
import com.nexus.iptv.feature.settings.ui.NexusSettingsScreen
import com.nexus.iptv.feature.settings.ui.UniversalSearchScreen
import com.nexus.iptv.feature.sources.ui.SourcesManagerScreen
import com.nexus.iptv.feature.vod.ui.VodSectionScreen
import com.nexus.iptv.theme.NexusIptvTheme
import com.nexus.iptv.viewmodel.NexusAppCoordinatorViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val coordinator: NexusAppCoordinatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiPrefs: NexusUiPreferences by coordinator.uiPreferences.collectAsState()
            val selectedDestination: NexusDestination by coordinator.selectedDestination.collectAsState()
            val formFactor = DeviceFormFactorDetector.detect(this)
            val accentColor = Color(uiPrefs.accentScheme.primaryHex)

            NexusIptvTheme(preferences = uiPrefs) {
                NexusAppRoot(
                    activity = this,
                    coordinator = coordinator,
                    uiPrefs = uiPrefs,
                    selectedDestination = selectedDestination,
                    formFactor = formFactor,
                    accentColor = accentColor
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (coordinator.selectedDestination.value != NexusDestination.DASHBOARD) {
            coordinator.navigateTo(NexusDestination.DASHBOARD)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NexusAppRoot(
    activity: ComponentActivity,
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    selectedDestination: NexusDestination,
    formFactor: DeviceFormFactor,
    accentColor: Color
) {
    when (selectedDestination) {
        NexusDestination.DASHBOARD -> DashboardRoute(coordinator, uiPrefs, selectedDestination, formFactor, accentColor)
        NexusDestination.LIVE_TV -> LiveTvRoute(activity, coordinator, uiPrefs, accentColor)
        NexusDestination.SOURCES_MANAGER -> SourcesRoute(coordinator, accentColor)
        NexusDestination.EPG_GUIDE -> EpgRoute(coordinator, uiPrefs, accentColor)
        NexusDestination.VOD_MOVIES -> VodRoute(coordinator, uiPrefs, accentColor)
        NexusDestination.DVR_RECORDINGS -> DvrRoute(activity, coordinator, uiPrefs, accentColor)
        NexusDestination.MULTIVIEW -> MultiviewRoute(coordinator, accentColor)
        NexusDestination.NETWORK_DIAGNOSTICS -> DiagnosticsRoute(coordinator, uiPrefs, accentColor)
        NexusDestination.GLOBAL_SEARCH -> SearchRoute(coordinator, accentColor)
        NexusDestination.SETTINGS -> SettingsRoute(coordinator, uiPrefs, accentColor)
    }
}

@Composable
private fun DashboardRoute(
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    selectedDestination: NexusDestination,
    formFactor: DeviceFormFactor,
    accentColor: Color
) {
    val dashboardState by coordinator.dashboardState.collectAsState()
    if (formFactor == DeviceFormFactor.TV_DPAD) {
        NexusTvDashboardScreen(
            uiState = dashboardState,
            selectedDestination = selectedDestination,
            accentColor = accentColor,
            surfaceAlpha = uiPrefs.surfaceAlpha,
            onSelectDestination = coordinator::navigateTo,
            onOpenChannel = coordinator::playLiveChannelById,
            onOpenCategory = { coordinator.navigateTo(NexusDestination.LIVE_TV) },
            onOpenVodItem = { coordinator.navigateTo(NexusDestination.VOD_MOVIES) }
        )
    } else {
        NexusMobileDashboardScreen(
            uiState = dashboardState,
            isTabletOrLandscape = formFactor == DeviceFormFactor.TABLET_OR_LANDSCAPE,
            selectedDestination = selectedDestination,
            accentColor = accentColor,
            surfaceAlpha = uiPrefs.surfaceAlpha,
            onSelectDestination = coordinator::navigateTo,
            onOpenChannel = coordinator::playLiveChannelById,
            onOpenCategory = { coordinator.navigateTo(NexusDestination.LIVE_TV) }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveTvRoute(
    activity: ComponentActivity,
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    accentColor: Color
) {
    val liveChannels by coordinator.liveChannels.collectAsState()
    val categories by coordinator.categories.collectAsState()
    val activeChannel by coordinator.activeChannel.collectAsState()
    val isFullscreen by coordinator.isFullscreenPlayback.collectAsState()
    val playerState by coordinator.playerEngine.playerState.collectAsState()
    val telemetry by coordinator.playerEngine.telemetry.collectAsState()
    val audioTracks by coordinator.playerEngine.audioTracks.collectAsState()
    val subtitleTracks by coordinator.playerEngine.subtitleTracks.collectAsState()
    val qualityTracks by coordinator.playerEngine.videoQualityTracks.collectAsState()
    val aspectRatio by coordinator.playerEngine.aspectRatioMode.collectAsState()
    val audioDelay by coordinator.playerEngine.audioDelayMs.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        val currentCh = activeChannel
        if (isFullscreen && currentCh != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = coordinator.playerEngine.getUnderlyingExoPlayer()
                    }
                },
                update = { view ->
                    view.player = coordinator.playerEngine.getUnderlyingExoPlayer()
                },
                modifier = Modifier.fillMaxSize()
            )

            NexusVideoPlayerOverlay(
                channelNumber = currentCh.number,
                channelName = currentCh.name,
                currentProgramTitle = currentCh.currentProgramTitle,
                nextProgramTitle = currentCh.nextProgramTitle,
                programProgress = currentCh.currentProgramProgress,
                playerState = playerState,
                telemetry = telemetry,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                videoQualityTracks = qualityTracks,
                activeAspectRatio = aspectRatio,
                activeDecoderMode = coordinator.decoderMode.value,
                audioDelayMs = audioDelay,
                accentColor = accentColor,
                onTogglePlayPause = {
                    val isPlaying = (playerState as? PlayerState.Ready)?.isPlaying == true
                    if (isPlaying) coordinator.playerEngine.pause() else coordinator.playerEngine.resume()
                },
                onCycleAspectRatio = {
                    val modes = AspectRatioMode.entries
                    coordinator.playerEngine.setAspectRatio(modes[(aspectRatio.ordinal + 1) % modes.size])
                },
                onCycleDecoderMode = {},
                onSelectAudioTrack = coordinator.playerEngine::selectAudioTrack,
                onSelectSubtitleTrack = coordinator.playerEngine::selectSubtitleTrack,
                onSelectVideoQuality = coordinator.playerEngine::selectVideoQuality,
                onAdjustAudioDelay = coordinator.playerEngine::setAudioDelayMs,
                onEnterPip = {
                    ExternalPlayerLauncher.enterPictureInPicture(activity, telemetry.width, telemetry.height)
                },
                onOpenExternalPlayer = {
                    ExternalPlayerLauncher.launchExternalPlayer(activity, currentCh.streamUrl, currentCh.name)
                }
            )
        }

        LiveTvSectionScreen(
            allChannels = liveChannels,
            categories = categories,
            activeChannel = currentCh,
            isFullscreenPlayback = isFullscreen,
            accentColor = accentColor,
            surfaceAlpha = uiPrefs.surfaceAlpha,
            onSelectChannelForPlayback = { ch -> coordinator.playLiveChannel(ch, enterFullscreen = true) },
            onToggleFullscreen = coordinator::toggleFullscreen,
            onToggleFavorite = { ch ->
                coroutineScope.launch { coordinator.liveTvRepository.toggleFavorite(ch.id, ch.isFavorite) }
            },
            onHideChannel = { ch ->
                coroutineScope.launch { coordinator.liveTvRepository.hideChannel(ch.id) }
            },
            onToggleLockChannel = { ch ->
                coroutineScope.launch { coordinator.liveTvRepository.toggleChannelLock(ch.id, ch.isLocked) }
            }
        )
    }
}

@Composable
private fun SourcesRoute(
    coordinator: NexusAppCoordinatorViewModel,
    accentColor: Color
) {
    val sources by coordinator.sources.collectAsState()
    val statusMessage by coordinator.statusMessage.collectAsState()
    SourcesManagerScreen(
        sources = sources,
        statusBannerMessage = statusMessage,
        accentColor = accentColor,
        onAddM3uUrl = coordinator::addRemoteM3u,
        onAddXtream = coordinator::addXtreamSource,
        onSyncSource = coordinator::syncSource,
        onVerifySource = coordinator::verifySource,
        onDeleteSourceConfirmed = coordinator::deleteSource,
        onExportBackup = coordinator::exportBackup,
        onNavigateBack = { coordinator.navigateTo(NexusDestination.DASHBOARD) }
    )
}

@Composable
private fun EpgRoute(
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    accentColor: Color
) {
    val epgRows by coordinator.epgTimelineRows.collectAsState()
    val sources by coordinator.sources.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    EpgGuideTimelineScreen(
        timelineRows = epgRows,
        windowStartEpochMs = System.currentTimeMillis() - 3_600_000L,
        accentColor = accentColor,
        surfaceAlpha = uiPrefs.surfaceAlpha,
        onSelectChannelToWatch = { ch -> coordinator.playLiveChannelById(ch.id) },
        onSelectCatchupProgram = coordinator::playCatchupProgram,
        onToggleReminder = coordinator::toggleProgramReminder,
        onSaveManualEpgMapping = { id, xmltvId ->
            coroutineScope.launch {
                coordinator.epgRepository.setManualEpgMapping(id, xmltvId)
                coordinator.refreshEpgGrid()
            }
        },
        onAdjustEpgTimeshiftMinutes = { shift ->
            sources.firstOrNull()?.let { src ->
                coroutineScope.launch { coordinator.epgRepository.setSourceEpgTimeshiftMinutes(src.id, shift) }
            }
        },
        onRefreshEpg = coordinator::syncAllEpg
    )
}

@Composable
private fun VodRoute(
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    accentColor: Color
) {
    val vodCatalog by coordinator.vodCatalog.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val vodCategories: List<String> = remember(vodCatalog) {
        val unique = mutableSetOf<String>()
        for (item in vodCatalog) unique.add(item.categoryName)
        unique.toList()
    }
    val vodGenres: List<String> = remember(vodCatalog) {
        val unique = mutableSetOf<String>()
        for (item in vodCatalog) {
            val g = item.genre
            if (!g.isNullOrBlank()) unique.add(g)
        }
        unique.toList()
    }
    VodSectionScreen(
        allVodItems = vodCatalog,
        categories = vodCategories,
        genres = vodGenres,
        episodesBySeriesId = emptyMap(),
        accentColor = accentColor,
        surfaceAlpha = uiPrefs.surfaceAlpha,
        onPlayMovie = { movie: VodItemEntity, startPos: Long ->
            val url = movie.streamUrl
            if (url != null) {
                coordinator.playerEngine.prepareAndPlay(url, startPositionMs = startPos)
            }
        },
        onPlayEpisode = { _: VodItemEntity, ep: SeriesEpisodeEntity, startPos: Long ->
            coordinator.playerEngine.prepareAndPlay(ep.streamUrl, startPositionMs = startPos)
        },
        onToggleFavorite = { vod: VodItemEntity ->
            coroutineScope.launch { coordinator.vodRepository.toggleVodFavorite(vod.id, vod.isFavorite) }
        }
    )
}

@Composable
private fun DvrRoute(
    activity: ComponentActivity,
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    accentColor: Color
) {
    val dvrRecordings by coordinator.dvrRecordings.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val dvrDir = remember { File(activity.filesDir, "dvr_recordings") }
    DvrAndTimeshiftScreen(
        recordings = dvrRecordings,
        activeChannelCapabilities = coordinator.evaluateActiveChannelDvrCapabilities(),
        retentionPolicy = DvrRetentionPolicy(),
        deviceFreeSpaceBytes = activity.filesDir.usableSpace,
        accentColor = accentColor,
        surfaceAlpha = uiPrefs.surfaceAlpha,
        onPlayRecording = { rec -> coordinator.playerEngine.prepareAndPlay(rec.outputFilePath) },
        onStopActiveRecording = { rec -> coroutineScope.launch { coordinator.dvrEngine.stopRecording(rec.id) } },
        onTogglePinRecording = { rec -> coroutineScope.launch { coordinator.dvrEngine.togglePinRecording(rec) } },
        onDeleteRecording = { rec -> coroutineScope.launch { coordinator.dvrEngine.deleteRecordingAndFile(rec) } },
        onRunAutoCleanupNow = {
            coroutineScope.launch { coordinator.dvrEngine.enforceStorageRetention(dvrDir, DvrRetentionPolicy()) }
        }
    )
}

@Composable
private fun MultiviewRoute(
    coordinator: NexusAppCoordinatorViewModel,
    accentColor: Color
) {
    val rawChannels by coordinator.rawChannelEntities.collectAsState()
    MultiviewSectionScreen(
        availableChannels = rawChannels,
        hardwareCapability = coordinator.multiviewCapability,
        accentColor = accentColor,
        onAssignChannelToSlot = { slot, ch ->
            coordinator.multiviewPool.bindChannelToSlot(slot, ch, isPrimaryWindow = slot == 0, isAudioActive = slot == 0)
        },
        onAudioFocusChanged = coordinator.multiviewPool::updateAudioFocus,
        onExpandChannelFullscreen = { ch -> coordinator.playLiveChannelById(ch.id) }
    )
}

@Composable
private fun DiagnosticsRoute(
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    accentColor: Color
) {
    val internetDiag by coordinator.internetDiagnostic.collectAsState()
    val isRunningSpeedtest by coordinator.isRunningSpeedtest.collectAsState()
    val sources by coordinator.sources.collectAsState()
    val sourceProbes by coordinator.sourceProbes.collectAsState()
    val telemetry by coordinator.playerEngine.telemetry.collectAsState()
    val smartStatus by coordinator.smartStreamEngine.status.collectAsState()
    NetworkDiagnosticsScreen(
        internetSnapshot = internetDiag,
        isRunningSpeedtest = isRunningSpeedtest,
        sources = sources,
        sourceProbeResults = sourceProbes,
        playerTelemetry = telemetry,
        smartStreamStatus = smartStatus,
        accentColor = accentColor,
        surfaceAlpha = uiPrefs.surfaceAlpha,
        onRunInternetDiagnostic = coordinator::runInternetSpeedtest,
        onVerifySourceClick = coordinator::verifySource
    )
}

@Composable
private fun SearchRoute(
    coordinator: NexusAppCoordinatorViewModel,
    accentColor: Color
) {
    val rawChannels by coordinator.rawChannelEntities.collectAsState()
    val vodCatalog by coordinator.vodCatalog.collectAsState()
    UniversalSearchScreen(
        channels = rawChannels,
        programs = emptyList(),
        vodItems = vodCatalog,
        recentQueries = emptyList(),
        accentColor = accentColor,
        onSelectSearchHit = { hit ->
            if (hit.category.name == "CHANNELS") coordinator.playLiveChannelById(hit.entityId)
            else coordinator.navigateTo(NexusDestination.VOD_MOVIES)
        },
        onSaveSearchQuery = {}
    )
}

@Composable
private fun SettingsRoute(
    coordinator: NexusAppCoordinatorViewModel,
    uiPrefs: NexusUiPreferences,
    accentColor: Color
) {
    NexusSettingsScreen(
        uiPreferences = uiPrefs,
        memoryBudget = coordinator.memoryBudget,
        profiles = listOf(
            UserProfileConfig(id = 1L, name = "Основной профиль"),
            UserProfileConfig(id = 2L, name = "Детский", isKidsProfile = true)
        ),
        remoteBindings = mapOf(
            CustomizableRemoteButton.COLOR_RED to RemoteCustomAction.START_DVR_RECORDING,
            CustomizableRemoteButton.COLOR_GREEN to RemoteCustomAction.OPEN_MULTIVIEW,
            CustomizableRemoteButton.COLOR_YELLOW to RemoteCustomAction.TOGGLE_FAVORITE,
            CustomizableRemoteButton.COLOR_BLUE to RemoteCustomAction.OPEN_EPG_GUIDE
        ),
        accentColor = accentColor,
        onChangeThemeMode = coordinator::updateThemeMode,
        onChangeAccentScheme = coordinator::updateAccentScheme,
        onChangeSurfaceAlpha = {},
        onChangeUiScale = {},
        onChangeStartupBehavior = {},
        onSelectProfile = {},
        onCycleRemoteBinding = {},
        onClearImageCacheNow = { Runtime.getRuntime().gc() }
    )
}
