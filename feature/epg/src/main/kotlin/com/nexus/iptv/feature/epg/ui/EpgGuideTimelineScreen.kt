package com.nexus.iptv.feature.epg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import com.nexus.iptv.feature.epg.repository.EpgChannelTimelineRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgGuideTimelineScreen(
    timelineRows: List<EpgChannelTimelineRow>,
    windowStartEpochMs: Long,
    windowHours: Int = 6,
    nowEpochMs: Long = System.currentTimeMillis(),
    currentEpgTimeshiftMinutes: Int = 0,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelectChannelToWatch: (ChannelEntity) -> Unit,
    onSelectCatchupProgram: (ChannelEntity, EpgProgramEntity) -> Unit,
    onToggleReminder: (ChannelEntity, EpgProgramEntity) -> Unit,
    onSaveManualEpgMapping: (channelId: Long, manualXmltvId: String?) -> Unit,
    onAdjustEpgTimeshiftMinutes: (Int) -> Unit,
    onRefreshEpg: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedProgramPair by remember { mutableStateOf<Pair<ChannelEntity, EpgProgramEntity>?>(null) }
    var manualMappingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var shiftMinutesState by remember { mutableIntStateOf(currentEpgTimeshiftMinutes) }

    val horizontalScrollState = rememberScrollState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val dpPerMinute = 4f
    val windowEndEpochMs = windowStartEpochMs + windowHours * 3_600_000L

    val filteredRows = remember(timelineRows, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) timelineRows
        else timelineRows.mapNotNull { row ->
            val channelMatches = row.channel.name.lowercase().contains(q)
            val matchingProgs = row.programs.filter {
                it.title.lowercase().contains(q) || (it.description?.lowercase()?.contains(q) == true)
            }
            when {
                channelMatches -> row
                matchingProgs.isNotEmpty() -> row.copy(programs = matchingProgs)
                else -> null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск по названию передачи, описанию или каналу") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                EpgActionButton(
                    text = "Сдвиг EPG: -30 мин",
                    accentColor = Color(0xFF475569),
                    onClick = {
                        shiftMinutesState -= 30
                        onAdjustEpgTimeshiftMinutes(shiftMinutesState)
                    }
                )
                Text(
                    text = "${if (shiftMinutesState >= 0) "+" else ""}${shiftMinutesState} мин",
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                EpgActionButton(
                    text = "+30 мин",
                    accentColor = Color(0xFF475569),
                    onClick = {
                        shiftMinutesState += 30
                        onAdjustEpgTimeshiftMinutes(shiftMinutesState)
                    }
                )
                EpgActionButton(
                    text = "Обновить XMLTV",
                    accentColor = accentColor,
                    onClick = onRefreshEpg
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp))
                        .background(Color(0xFF101726))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("КАНАЛ / TVG-ID", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                Row(
                    modifier = Modifier
                        .horizontalScroll(horizontalScrollState)
                        .height(40.dp)
                        .background(Color(0xFF0D1422)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalHalfHours = windowHours * 2
                    for (slot in 0 until totalHalfHours) {
                        val slotTimeMs = windowStartEpochMs + slot * 1_800_000L
                        val slotWidthDp = (30 * dpPerMinute).dp
                        Box(
                            modifier = Modifier
                                .width(slotWidthDp)
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = timeFormat.format(Date(slotTimeMs)),
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRows, key = { it.channel.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141C2B).copy(alpha = surfaceAlpha))
                                .nexusTvFocusable(
                                    accentColor = accentColor,
                                    cornerRadius = 10.dp,
                                    onSelect = { onSelectChannelToWatch(row.channel) },
                                    onContextMenu = { manualMappingChannel = row.channel }
                                )
                                .clickable { onSelectChannelToWatch(row.channel) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "#${row.channel.channelNumber} ${row.channel.name}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "EPG: ${row.effectiveEpgId ?: "Не сопоставлен"}",
                                color = if (row.effectiveEpgId != null) accentColor else Color(0xFF64748B),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            modifier = Modifier
                                .horizontalScroll(horizontalScrollState)
                                .fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (row.programs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .width((windowHours * 60 * dpPerMinute).dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0E1523))
                                        .clickable { manualMappingChannel = row.channel }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "Нет данных телепрограммы • Нажмите для ручного сопоставления EPG",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                row.programs.forEach { program ->
                                    val clippedStart = maxOf(program.startEpochMs, windowStartEpochMs)
                                    val clippedEnd = minOf(program.endEpochMs, windowEndEpochMs)
                                    val durationMinutes = ((clippedEnd - clippedStart) / 60_000L).coerceAtLeast(15L)
                                    val cellWidthDp = (durationMinutes * dpPerMinute).dp
                                    val isCurrentlyAiring = nowEpochMs in program.startEpochMs..program.endEpochMs

                                    Column(
                                        modifier = Modifier
                                            .width(cellWidthDp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCurrentlyAiring) accentColor.copy(alpha = 0.24f)
                                                else Color(0xFF131B2E)
                                            )
                                            .nexusTvFocusable(
                                                accentColor = accentColor,
                                                cornerRadius = 8.dp,
                                                focusedScale = 1.02f,
                                                onSelect = { selectedProgramPair = row.channel to program }
                                            )
                                            .clickable { selectedProgramPair = row.channel to program }
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = program.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCurrentlyAiring) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (program.hasReminder) {
                                                Text(text = "🔔", fontSize = 12.sp)
                                            }
                                        }

                                        Text(
                                            text = "${timeFormat.format(Date(program.startEpochMs))} – ${timeFormat.format(Date(program.endEpochMs))}",
                                            color = if (isCurrentlyAiring) accentColor else Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedProgramPair?.let { (channel, program) ->
            ProgramDetailsDialog(
                channel = channel,
                program = program,
                nowEpochMs = nowEpochMs,
                accentColor = accentColor,
                onWatchLive = {
                    selectedProgramPair = null
                    onSelectChannelToWatch(channel)
                },
                onWatchCatchup = {
                    selectedProgramPair = null
                    onSelectCatchupProgram(channel, program)
                },
                onToggleReminder = {
                    onToggleReminder(channel, program)
                    selectedProgramPair = null
                },
                onOpenManualMapping = {
                    selectedProgramPair = null
                    manualMappingChannel = channel
                },
                onDismiss = { selectedProgramPair = null }
            )
        }

        manualMappingChannel?.let { channel ->
            ManualEpgMappingDialog(
                channel = channel,
                accentColor = accentColor,
                onSave = { manualId ->
                    onSaveManualEpgMapping(channel.id, manualId)
                    manualMappingChannel = null
                },
                onDismiss = { manualMappingChannel = null }
            )
        }
    }
}

@Composable
private fun ProgramDetailsDialog(
    channel: ChannelEntity,
    program: EpgProgramEntity,
    nowEpochMs: Long,
    accentColor: Color,
    onWatchLive: () -> Unit,
    onWatchCatchup: () -> Unit,
    onToggleReminder: () -> Unit,
    onOpenManualMapping: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateTimeFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val isPastProgram = program.endEpochMs < nowEpochMs
    val isFutureProgram = program.startEpochMs > nowEpochMs
    val canWatchCatchup = isPastProgram && channel.catchupDays > 0 && !channel.catchupType.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101829))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = program.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${channel.name} • ${dateTimeFormat.format(Date(program.startEpochMs))} – ${dateTimeFormat.format(Date(program.endEpochMs))}",
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = program.description ?: "Подробное описание передачи не предоставлено источником XMLTV.",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EpgActionButton(text = "Смотреть прямой эфир", accentColor = accentColor, onClick = onWatchLive)

                if (canWatchCatchup) {
                    EpgActionButton(text = "Смотреть из архива (Catch-up)", accentColor = Color(0xFF00E676), onClick = onWatchCatchup)
                }

                if (isFutureProgram) {
                    EpgActionButton(
                        text = if (program.hasReminder) "Отменить напоминание" else "🔔 Напомнить о начале",
                        accentColor = Color(0xFFFFD54F),
                        onClick = onToggleReminder
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EpgActionButton(text = "Ручная привязка EPG ID", accentColor = Color(0xFF38BDF8), onClick = onOpenManualMapping)
                EpgActionButton(text = "Закрыть", accentColor = Color(0xFF475569), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ManualEpgMappingDialog(
    channel: ChannelEntity,
    accentColor: Color,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var manualIdInput by remember {
        mutableStateOf(channel.manualEpgChannelId ?: channel.epgChannelId.orEmpty())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121A2B))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Ручное сопоставление EPG",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Канал: #${channel.channelNumber} ${channel.name}",
                color = accentColor,
                fontSize = 14.sp
            )
            OutlinedTextField(
                value = manualIdInput,
                onValueChange = { manualIdInput = it },
                label = { Text("Введите ID канала из XMLTV (например, discovery.sci)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EpgActionButton(
                    text = "Сохранить привязку",
                    accentColor = accentColor,
                    onClick = { onSave(manualIdInput.takeIf { it.isNotBlank() }) }
                )
                EpgActionButton(
                    text = "Сбросить на Авто",
                    accentColor = Color(0xFFF59E0B),
                    onClick = { onSave(null) }
                )
                EpgActionButton(
                    text = "Отмена",
                    accentColor = Color(0xFF475569),
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun EpgActionButton(
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
