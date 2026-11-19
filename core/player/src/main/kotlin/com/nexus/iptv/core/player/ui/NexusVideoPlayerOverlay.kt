package com.nexus.iptv.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.player.contract.AspectRatioMode
import com.nexus.iptv.core.player.contract.DecoderMode
import com.nexus.iptv.core.player.contract.PlaybackTelemetry
import com.nexus.iptv.core.player.contract.PlayerState
import com.nexus.iptv.core.player.contract.TrackInfo
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable

/**
 * Advanced Player Overlay — расширенная профессиональная панель управления видеоплеером (п. 8 и п. 19 ТЗ).
 *
 * Включает:
 * - Информационный баннер канала и текущей/следующей передачи EPG;
 * - Панель технической информации (разрешение, FPS, видео/аудио декодер HW/SW, битрейт, буфер, время старта, дропы);
 * - Переключение аудиодорожек, субтитров, качества видеопотока, режима декодера и соотношения сторон;
 * - Управление задержкой звука (-3000..+3000 мс с шагом 50 мс);
 * - Кнопки Picture-in-Picture и запуска внешнего проигрывателя.
 */
@Composable
fun NexusVideoPlayerOverlay(
    channelNumber: Int,
    channelName: String,
    currentProgramTitle: String?,
    nextProgramTitle: String?,
    programProgress: Float,
    playerState: PlayerState,
    telemetry: PlaybackTelemetry,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    videoQualityTracks: List<TrackInfo>,
    activeAspectRatio: AspectRatioMode,
    activeDecoderMode: DecoderMode,
    audioDelayMs: Long,
    accentColor: Color,
    onTogglePlayPause: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onCycleDecoderMode: () -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitleTrack: (String?) -> Unit,
    onSelectVideoQuality: (String?) -> Unit,
    onAdjustAudioDelay: (Long) -> Unit,
    onEnterPip: () -> Unit,
    onOpenExternalPlayer: () -> Unit
) {
    var showTechHud by remember { mutableStateOf(true) }
    var showTracksSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Верхняя панель: Технический HUD (п. 8 и п. 17 ТЗ — отображение технической информации)
        if (showTechHud) {
            TechnicalTelemetryHud(
                telemetry = telemetry,
                playerState = playerState,
                decoderMode = activeDecoderMode,
                aspectRatio = activeAspectRatio,
                audioDelayMs = audioDelayMs,
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
            )
        }

        // 2. Модальная панель выбора аудиодорожек, субтитров, качества и синхронизации звука
        if (showTracksSheet) {
            TracksAndDelayDialog(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                videoQualityTracks = videoQualityTracks,
                audioDelayMs = audioDelayMs,
                accentColor = accentColor,
                onSelectAudio = onSelectAudioTrack,
                onSelectSubtitle = onSelectSubtitleTrack,
                onSelectQuality = onSelectVideoQuality,
                onAdjustAudioDelay = onAdjustAudioDelay,
                onDismiss = { showTracksSheet = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        // 3. Нижний инфо-бар канала и панель инструментов плеера
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xE6060911),
                            Color(0xF505070D)
                        )
                    )
                )
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#$channelNumber  $channelName",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Сейчас: ${currentProgramTitle ?: "Прямая трансляция"}",
                        color = accentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Далее: ${nextProgramTitle ?: "Нет данных телепрограммы"}",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Бейдж разрешения и аппаратного ускорения
                val resolutionLabel = if (telemetry.height >= 2160) "4K UHD"
                else if (telemetry.height >= 1080) "FHD 1080p"
                else if (telemetry.height >= 720) "HD 720p"
                else "SD"
                val hwLabel = if (telemetry.isHardwareAccelerated) "HW" else "SW"

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayBadge(text = resolutionLabel, color = accentColor)
                    OverlayBadge(text = "$hwLabel • ${telemetry.videoCodec.take(14)}", color = Color(0xFF38BDF8))
                }
            }

            LinearProgressIndicator(
                progress = { programProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.16f)
            )

            // Кнопки управления воспроизведением (полная поддержка пульта D-Pad и Touch)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isPlaying = (playerState as? PlayerState.Ready)?.isPlaying == true
                OverlayControlButton(
                    label = if (isPlaying) "Пауза" else "Воспроизвести",
                    accentColor = accentColor,
                    onClick = onTogglePlayPause
                )
                OverlayControlButton(
                    label = "Дорожки и Задержка (${audioTracks.size} А / ${subtitleTracks.size} С)",
                    accentColor = accentColor,
                    onClick = { showTracksSheet = !showTracksSheet }
                )
                OverlayControlButton(
                    label = "Кадр: ${activeAspectRatio.name}",
                    accentColor = accentColor,
                    onClick = onCycleAspectRatio
                )
                OverlayControlButton(
                    label = "Декодер: ${activeDecoderMode.name}",
                    accentColor = accentColor,
                    onClick = onCycleDecoderMode
                )
                OverlayControlButton(
                    label = if (showTechHud) "Скрыть инфо" else "Тех. инфо",
                    accentColor = accentColor,
                    onClick = { showTechHud = !showTechHud }
                )
                OverlayControlButton(
                    label = "PiP",
                    accentColor = accentColor,
                    onClick = onEnterPip
                )
                OverlayControlButton(
                    label = "Внешний плеер",
                    accentColor = accentColor,
                    onClick = onOpenExternalPlayer
                )
            }
        }
    }
}

@Composable
private fun TechnicalTelemetryHud(
    telemetry: PlaybackTelemetry,
    playerState: PlayerState,
    decoderMode: DecoderMode,
    aspectRatio: AspectRatioMode,
    audioDelayMs: Long,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(310.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xD90A0F1D))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "ТЕЛЕМЕТРИЯ ПОТОКА (MEDIA3)",
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
        val statusText = when (playerState) {
            is PlayerState.Idle -> "Ожидание"
            is PlayerState.Buffering -> "Буферизация (${playerState.percent}%)"
            is PlayerState.Ready -> if (playerState.isPlaying) "Активен (Playing)" else "Пауза"
            is PlayerState.Ended -> "Завершён"
            is PlayerState.Failed -> "Ошибка: ${playerState.errorCode}"
        }
        HudRow("Статус:", statusText)
        HudRow("Разрешение:", "${telemetry.width}×${telemetry.height} @ ${telemetry.frameRate.toInt()} fps")
        HudRow("Видео декодер:", "${telemetry.videoCodec} (${if (telemetry.isHardwareAccelerated) "HW" else "SW"})")
        HudRow("Аудио декодер:", telemetry.audioCodec)
        HudRow("Битрейт / Буфер:", "${telemetry.currentBitrateKbps} kbps / ${telemetry.bufferedDurationMs / 1000f} сек")
        HudRow("Время запуска:", "${telemetry.startupDurationMs} мс")
        HudRow("Пропущено кадров:", "${telemetry.droppedFramesCount} (ребуферов: ${telemetry.rebufferCount})")
        HudRow("Режим / Кадр:", "${decoderMode.name} / ${aspectRatio.name} (Аудио: ${audioDelayMs} мс)")
    }
}

@Composable
private fun TracksAndDelayDialog(
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    videoQualityTracks: List<TrackInfo>,
    audioDelayMs: Long,
    accentColor: Color,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onSelectQuality: (String?) -> Unit,
    onAdjustAudioDelay: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(560.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF20E1525))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Управление дорожками, качеством и синхронизацией",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            OverlayControlButton(label = "Закрыть", accentColor = accentColor, onClick = onDismiss)
        }

        // 1. Выбор аудиодорожки
        Text("Аудиодорожки:", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (audioTracks.isEmpty()) {
                Text("Стандартная дорожка потока", color = Color(0xFF94A3B8), fontSize = 12.sp)
            } else {
                audioTracks.forEach { track ->
                    OverlayControlButton(
                        label = if (track.isSelected) "✓ ${track.label}" else track.label,
                        accentColor = if (track.isSelected) accentColor else Color(0xFF475569),
                        onClick = { onSelectAudio(track.id) }
                    )
                }
            }
        }

        // 2. Выбор субтитров
        Text("Субтитры:", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayControlButton(
                label = "Отключить",
                accentColor = Color(0xFF475569),
                onClick = { onSelectSubtitle(null) }
            )
            subtitleTracks.forEach { track ->
                OverlayControlButton(
                    label = if (track.isSelected) "✓ ${track.label}" else track.label,
                    accentColor = if (track.isSelected) accentColor else Color(0xFF475569),
                    onClick = { onSelectSubtitle(track.id) }
                )
            }
        }

        // 3. Выбор качества потока (ABR или фиксированный профиль)
        Text("Качество видеопотока:", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayControlButton(
                label = "Авто (ABR)",
                accentColor = accentColor,
                onClick = { onSelectQuality(null) }
            )
            videoQualityTracks.take(4).forEach { track ->
                OverlayControlButton(
                    label = if (track.isSelected) "✓ ${track.label}" else track.label,
                    accentColor = if (track.isSelected) accentColor else Color(0xFF475569),
                    onClick = { onSelectQuality(track.id) }
                )
            }
        }

        // 4. Компенсация задержки звука
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Задержка аудио: ${audioDelayMs} мс",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            OverlayControlButton(
                label = "-50 мс",
                accentColor = accentColor,
                onClick = { onAdjustAudioDelay(audioDelayMs - 50L) }
            )
            OverlayControlButton(
                label = "Сброс (0 мс)",
                accentColor = Color(0xFF475569),
                onClick = { onAdjustAudioDelay(0L) }
            )
            OverlayControlButton(
                label = "+50 мс",
                accentColor = accentColor,
                onClick = { onAdjustAudioDelay(audioDelayMs + 50L) }
            )
        }
    }
}

@Composable
private fun HudRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun OverlayBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.20f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OverlayControlButton(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A2438))
            .nexusTvFocusable(accentColor = accentColor, cornerRadius = 8.dp, onSelect = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
