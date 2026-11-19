package com.nexus.iptv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.nexus.iptv.core.model.DeviceFormFactor
import com.nexus.iptv.core.model.NexusDestination
import com.nexus.iptv.core.player.contract.AspectRatioMode
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
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val coordinator: NexusAppCoordinatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiPrefs by coordinator.uiPreferences.collectAsState()
            val selectedDestination by coordinator.selectedDestination.collectAsState()
            val dashboardState by coordinator.dashboardState.collectAsState()
            val sources by coordinator.sources.collectAsState()
            val categories by coordinator.categories.collectAsState()
            val liveChannels by coordinator.liveChannels.collectAsState()
            val rawChannels by coordinator.rawChannelEntities.collectAsState()
            val vodCatalog by coordinator.vodCatalog.collectAsState()
            val dvrRecordings by coordinator.dvrRecordings.collectAsState()
            val activeChannel by coordinator.activeChannel.collectAsState()
            val isFullscreen by coordinator.isFullscreenPlayback.collectAsState()
            val statusMessage by coordinator.statusMessage.collectAsState()
            val internetDiag by coordinator.internetDiagnostic.collectAsState()
            val isRunningSpeedtest by coordinator.isRunningSpeedtest.collectAsState()
            val sourceProbes by coordinator.sourceProbes.collectAsState()
            val epgRows by coordinator.epgTimelineRows.collectAsState()

            val playerState by coordinator.playerEngine.playerState.collectAsState()
            val telemetry by coordinator.playerEngine.telemetry.collectAsState()
            val audioTracks by coordinator.playerEngine.audioTracks.collectAsState()
            val subtitleTracks by coordinator.playerEngine.subtitleTracks.collectAsState()
            val qualityTracks by coordinator.playerEngine.videoQualityTracks.collectAsState()
            val aspectRatio by coordinator.playerEngine.aspectRatioMode.collectAsState()
            val audioDelay by coordinator.playerEngine.audioDelayMs.collectAsState()
            val smartStatus by coordinator.smartStreamEngine.status.collectAsState()

            val formFactor = DeviceFormFactorDetector.detect(this)
            val accentColor = Color(uiPrefs.accentScheme.primaryHex)
            val coroutineScope = rememberCoroutineScope()

            NexusIptvTheme(preferences = uiPrefs) {
                when (selectedDestination) {
                    NexusDestination.DASHBOARD -> {
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

                    NexusDestination.LIVE_TV -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isFullscreen && activeChannel != null) {
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
                                    channelNumber = activeChannel!!.number,
                                    channelName = activeChannel!!.name,
                                    currentProgramTitle = activeChannel!!.currentProgramTitle,
                                    nextProgramTitle = activeChannel!!.nextProgramTitle,
                                    programProgress = activeChannel!!.currentProgramProgress,
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
                                        val isPlaying = (playerState as? com.nexus.iptv.core.player.contract.PlayerState.Ready)?.isPlaying == true
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
                                        ExternalPlayerLauncher.enterPictureInPicture(this@MainActivity, telemetry.width, telemetry.height)
                                    },
                                    onOpenExternalPlayer = {
                                        ExternalPlayerLauncher.launchExternalPlayer(this@MainActivity, activeChannel!!.streamUrl, activeChannel!!.name)
                                    }
                                )
                            }

                            LiveTvSectionScreen(
                                allChannels = liveChannels,
                                categories = categories,
                                activeChannel = activeChannel,
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

                    NexusDestination.SOURCES_MANAGER -> {
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

                    NexusDestination.EPG_GUIDE -> {
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

                    NexusDestination.VOD_MOVIES -> {
                        VodSectionScreen(
                            allVodItems = vodCatalog,
                            categories = vodCatalog.map { it.categoryName }.distinct(),
                            genres = vodCatalog.mapNotNull { it.genre }.distinct(),
                            episodesBySeriesId = emptyMap(),
                            accentColor = accentColor,
                            surfaceAlpha = uiPrefs.surfaceAlpha,
                            onPlayMovie = { movie, startPos ->
                                movie.streamUrl?.let { url ->
                                    coordinator.playerEngine.prepareAndPlay(url, startPositionMs = startPos)
                                }
                            },
                            onPlayEpisode = { _, ep, startPos ->
                                coordinator.playerEngine.prepareAndPlay(ep.streamUrl, startPositionMs = startPos)
                            },
                            onToggleFavorite = { vod ->
                                coroutineScope.launch { coordinator.vodRepository.toggleVodFavorite(vod.id, vod.isFavorite) }
                            }
                        )
                    }

                    NexusDestination.DVR_RECORDINGS -> {
                        val dvrDir = remember { File(filesDir, "dvr_recordings") }
                        DvrAndTimeshiftScreen(
                            recordings = dvrRecordings,
                            activeChannelCapabilities = coordinator.evaluateActiveChannelDvrCapabilities(),
                            retentionPolicy = DvrRetentionPolicy(),
                            deviceFreeSpaceBytes = filesDir.usableSpace,
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

                    NexusDestination.MULTIVIEW -> {
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

                    NexusDestination.NETWORK_DIAGNOSTICS -> {
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

                    NexusDestination.GLOBAL_SEARCH -> {
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

                    NexusDestination.SETTINGS -> {
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
                }
            }
        }
    }

    override fun onBackPressed() {
        if (coordinator.selectedDestination.value != NexusDestination.DASHBOARD) {
            coordinator.navigateTo(NexusDestination.DASHBOARD)
        } else {
            super.onBackPressed()
        }
    }
}
