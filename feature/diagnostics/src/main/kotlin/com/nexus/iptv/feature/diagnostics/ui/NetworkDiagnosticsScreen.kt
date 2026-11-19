package com.nexus.iptv.feature.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.database.entity.SourceEntity
import com.nexus.iptv.core.player.contract.PlaybackTelemetry
import com.nexus.iptv.core.streamengine.policy.SmartStreamStatus
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import com.nexus.iptv.feature.diagnostics.domain.InternetDiagnosticSnapshot
import com.nexus.iptv.feature.diagnostics.domain.SourceProbeDiagnosticResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран сетевой и аппаратной диагностики — Network Diagnostics (Этап 12, п. 17 ТЗ).
 *
 * Показывает:
 * - Состояние интернета, тип сети, задержку соединения (Ping) и скорость загрузки тестового ресурса (Мбит/с);
 * - Состояние каждого IPTV-источника и доступность EPG с кнопкой «Проверить источник»;
 * - Время подключения, время запуска канала, количество переподключений, ошибки воспроизведения,
 *   информацию об аппаратном/программном декодере и статистику буферизации.
 */
@Composable
fun NetworkDiagnosticsScreen(
    internetSnapshot: InternetDiagnosticSnapshot?,
    isRunningSpeedtest: Boolean,
    sources: List<SourceEntity>,
    sourceProbeResults: Map<Long, SourceProbeDiagnosticResult>,
    playerTelemetry: PlaybackTelemetry,
    smartStreamStatus: SmartStreamStatus,
    accentColor: Color,
    surfaceAlpha: Float,
    onRunInternetDiagnostic: () -> Unit,
    onVerifySourceClick: (SourceEntity) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .padding(24.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Заголовок и кнопка запуска теста сети
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Диагностика сети, источников и видеодекодеров",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Комплексная проверка соединения, задержки, EPG, буферизации и аппаратного ускорения",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }

                DiagPillButton(
                    text = if (isRunningSpeedtest) "Выполняется замер..." else "Запустить тест сети",
                    accentColor = accentColor,
                    onClick = onRunInternetDiagnostic
                )
            }
        }

        // 2. Блок состояния интернета (понятный вывод + инженерная строка)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121A2A).copy(alpha = surfaceAlpha))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Состояние интернета и пропускная способность",
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (internetSnapshot == null) {
                    Text(
                        text = "Нажмите «Запустить тест сети», чтобы измерить пинг и реальную скорость загрузки.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MetricTile(
                            label = "Интернет / Тип",
                            value = if (internetSnapshot.isInternetConnected) internetSnapshot.transportType else "Офлайн",
                            valueColor = if (internetSnapshot.isInternetConnected) Color(0xFF00E676) else Color(0xFFEF4444),
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Задержка (Ping)",
                            value = "${internetSnapshot.latencyMs} мс",
                            valueColor = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Скорость загрузки",
                            value = "${"%.1f".format(internetSnapshot.downloadSpeedMbps)} Мбит/с",
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Готовность к видео",
                            value = internetSnapshot.readinessVerdict.titleRu,
                            valueColor = Color(0xFFFFD54F),
                            modifier = Modifier.weight(1.4f)
                        )
                    }

                    Text(
                        text = internetSnapshot.userSummary,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Технический отчёт: ${internetSnapshot.technicalDetails}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 3. Блок телеметрии активного видеопотока, декодера и переподключений
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121A2A).copy(alpha = surfaceAlpha))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "2. Телеметрия воспроизведения, декодера и буферизации",
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MetricTile(
                        label = "Время запуска канала",
                        value = "${playerTelemetry.startupDurationMs} мс",
                        valueColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Переподключения",
                        value = "${smartStreamStatus.retryAttempt} / ${smartStreamStatus.maxRetries}",
                        valueColor = if (smartStreamStatus.retryAttempt > 0) Color(0xFFF59E0B) else Color(0xFF00E676),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Буфер / Ребуферизации",
                        value = "${playerTelemetry.bufferedDurationMs / 1000f} с (остановок: ${playerTelemetry.rebufferCount})",
                        valueColor = Color.White,
                        modifier = Modifier.weight(1.2f)
                    )
                    MetricTile(
                        label = "Видео / Аудио декодер",
                        value = "${playerTelemetry.videoCodec} (${if (playerTelemetry.isHardwareAccelerated) "HW" else "SW"}) / ${playerTelemetry.audioCodec}",
                        valueColor = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1.5f)
                    )
                }

                Text(
                    text = "Состояние Smart Stream Engine: ${smartStreamStatus.diagnosticHint} (Health Score: ${smartStreamStatus.computedHealthScore}%)",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            }
        }

        // 4. Проверка IPTV-источников и EPG (Кнопка «Проверить источник» — п. 17 ТЗ)
        item {
            Text(
                text = "3. Проверка доступности IPTV-источников и XMLTV EPG (${sources.size})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(sources, key = { "diag_src_${it.id}" }) { source ->
            val probe = sourceProbeResults[source.id]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141C2B))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${source.name} (${source.sourceType})",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (probe == null) {
                        Text(
                            text = "Нажмите «Проверить источник» для диагностики сервера плейлиста и EPG.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            text = probe.userFriendlyMessage,
                            color = if (probe.isPlaylistReachable) Color(0xFF00E676) else Color(0xFFEF4444),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = probe.engineerLogLine,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                DiagPillButton(
                    text = "Проверить источник",
                    accentColor = accentColor,
                    onClick = { onVerifySourceClick(source) }
                )
            }
        }

        // 5. Журнал ошибок воспроизведения
        if (smartStreamStatus.recentErrors.isNotEmpty()) {
            item {
                Text(
                    text = "4. Журнал ошибок воспроизведения (${smartStreamStatus.recentErrors.size})",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(smartStreamStatus.recentErrors) { err ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A141C))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "[${timeFormat.format(Date(err.timestampEpochMs))}] ${err.channelName} • Код: ${err.errorCode}",
                        color = Color(0xFFF87171),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = err.diagnosticMessage, color = Color.White, fontSize = 12.sp)
                    Text(text = "Действие системы: ${err.recoveryActionTaken}", color = accentColor, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0C121E))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DiagPillButton(
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.24f))
            .nexusTvFocusable(accentColor = accentColor, cornerRadius = 10.dp, onSelect = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
