package com.nexus.iptv.feature.dvr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.database.entity.DvrRecordingEntity
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import com.nexus.iptv.feature.dvr.domain.StreamTimeCapabilities
import com.nexus.iptv.feature.dvr.storage.DvrRetentionPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран управления записями эфира (DVR), архивами (Catch-up), паузой эфира (Timeshift) и хранилищем (Этап 9, п. 10 ТЗ).
 *
 * Честно отображает технические возможности текущего канала и устройства (не имитирует недоступные функции).
 */
@Composable
fun DvrAndTimeshiftScreen(
    recordings: List<DvrRecordingEntity>,
    activeChannelCapabilities: StreamTimeCapabilities?,
    retentionPolicy: DvrRetentionPolicy,
    deviceFreeSpaceBytes: Long,
    accentColor: Color,
    surfaceAlpha: Float,
    onPlayRecording: (DvrRecordingEntity) -> Unit,
    onStopActiveRecording: (DvrRecordingEntity) -> Unit,
    onTogglePinRecording: (DvrRecordingEntity) -> Unit,
    onDeleteRecording: (DvrRecordingEntity) -> Unit,
    onRunAutoCleanupNow: () -> Unit
) {
    val totalUsedBytes = remember(recordings) { recordings.sumOf { it.recordedBytes } }
    val quotaFraction = (totalUsedBytes.toFloat() / retentionPolicy.maxStorageQuotaBytes.coerceAtLeast(1L).toFloat())
        .coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Заголовок и сводка хранилища DVR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Центр DVR, Архивов (Catch-up) и Timeshift",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Занято записями: ${totalUsedBytes / 1048576} МБ из ${retentionPolicy.maxStorageQuotaBytes / 1048576} МБ • Свободно на устройстве: ${deviceFreeSpaceBytes / 1048576} МБ",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
            DvrActionButton(
                text = "Очистить старые записи",
                accentColor = accentColor,
                onClick = onRunAutoCleanupNow
            )
        }

        LinearProgressIndicator(
            progress = { quotaFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (quotaFraction > 0.85f) Color(0xFFEF4444) else accentColor,
            trackColor = Color.White.copy(alpha = 0.12f)
        )

        // 2. Честный индикатор технической поддержки для активного канала (п. 10 ТЗ — без имитации)
        activeChannelCapabilities?.let { caps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CapabilityStatusCard(
                    title = "Catch-up (Архив провайдера)",
                    isSupported = caps.isCatchupSupported,
                    details = caps.catchupReason,
                    modifier = Modifier.weight(1f)
                )
                CapabilityStatusCard(
                    title = "Live Timeshift (Пауза эфира)",
                    isSupported = caps.isLiveTimeshiftSupported,
                    details = caps.timeshiftReason,
                    modifier = Modifier.weight(1f)
                )
                CapabilityStatusCard(
                    title = "Запись эфира (DVR)",
                    isSupported = caps.isDvrRecordingSupported,
                    details = caps.dvrReason,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Список записей и запланированных заданий
        Text(
            text = "Мои записи и расписание DVR (${recordings.size})",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(recordings, key = { it.id }) { rec ->
                DvrRecordingRow(
                    recording = rec,
                    accentColor = accentColor,
                    surfaceAlpha = surfaceAlpha,
                    onPlay = { onPlayRecording(rec) },
                    onStop = { onStopActiveRecording(rec) },
                    onTogglePin = { onTogglePinRecording(rec) },
                    onDelete = { onDeleteRecording(rec) }
                )
            }
        }
    }
}

@Composable
private fun CapabilityStatusCard(
    title: String,
    isSupported: Boolean,
    details: String,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isSupported) Color(0xFF00E676) else Color(0xFFEF4444)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121A2A))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (isSupported) "ДОСТУПНО" else "НЕДОСТУПНО",
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Text(
            text = details,
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DvrRecordingRow(
    recording: DvrRecordingEntity,
    accentColor: Color,
    surfaceAlpha: Float,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val sizeMb = recording.recordedBytes / 1_048_576L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141C2B).copy(alpha = surfaceAlpha))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = recording.programTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (recording.isPinned) {
                    Text(
                        text = "[Защищено от автоудаления]",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "${recording.channelName} • ${dateFormat.format(Date(recording.scheduledStartEpochMs))} – ${dateFormat.format(Date(recording.scheduledEndEpochMs))}",
                color = accentColor,
                fontSize = 13.sp
            )
            Text(
                text = "Статус: ${recording.status} • Объём: $sizeMb МБ" +
                    (recording.failureReason?.let { " • Причина: $it" } ?: ""),
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recording.status == "COMPLETED" && recording.recordedBytes > 0L) {
                DvrActionButton(text = "▶ Смотреть запись", accentColor = Color(0xFF00E676), onClick = onPlay)
            }
            if (recording.status == "RECORDING") {
                DvrActionButton(text = "■ Остановить запись", accentColor = Color(0xFFF59E0B), onClick = onStop)
            }
            DvrActionButton(
                text = if (recording.isPinned) "Снять защиту" else "Закрепить",
                accentColor = Color(0xFF38BDF8),
                onClick = onTogglePin
            )
            DvrActionButton(
                text = "Удалить",
                accentColor = Color(0xFFEF4444),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun DvrActionButton(
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.22f))
            .nexusTvFocusable(accentColor = accentColor, cornerRadius = 10.dp, onSelect = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
