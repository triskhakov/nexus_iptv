package com.nexus.iptv.core.player.contract

import kotlinx.coroutines.flow.StateFlow

enum class DecoderMode { AUTO, HARDWARE_PREFERRED, SOFTWARE_PREFERRED }
enum class AspectRatioMode { FIT, FILL, ZOOM, RATIO_16_9, RATIO_4_3 }

data class TrackInfo(
    val id: String,
    val label: String,
    val language: String?,
    val codec: String?,
    val bitrate: Int,
    val isSelected: Boolean
)

data class PlaybackTelemetry(
    val startupDurationMs: Long = 0L,
    val videoCodec: String = "N/A",
    val audioCodec: String = "N/A",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val isHardwareAccelerated: Boolean = true,
    val currentBitrateKbps: Long = 0L,
    val bufferedDurationMs: Long = 0L,
    val droppedFramesCount: Int = 0,
    val rebufferCount: Int = 0
)

sealed interface PlayerState {
    data object Idle : PlayerState
    data class Buffering(val percent: Int) : PlayerState
    data class Ready(val isPlaying: Boolean) : PlayerState
    data object Ended : PlayerState
    data class Failed(val errorCode: String, val message: String, val isRecoverable: Boolean) : PlayerState
}

interface VideoPlayerEngine {
    val playerState: StateFlow<PlayerState>
    val telemetry: StateFlow<PlaybackTelemetry>
    val audioTracks: StateFlow<List<TrackInfo>>
    val subtitleTracks: StateFlow<List<TrackInfo>>
    val videoQualityTracks: StateFlow<List<TrackInfo>>

    fun prepareAndPlay(
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        decoderMode: DecoderMode = DecoderMode.AUTO,
        startPositionMs: Long = 0L
    )
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
    fun release()
    fun selectAudioTrack(trackId: String)
    fun selectSubtitleTrack(trackId: String?)
    fun selectVideoQuality(trackId: String?)
    fun setAudioDelayMs(delayMs: Long)
    fun setAspectRatio(mode: AspectRatioMode)
    fun setVolume(volume: Float)
}
