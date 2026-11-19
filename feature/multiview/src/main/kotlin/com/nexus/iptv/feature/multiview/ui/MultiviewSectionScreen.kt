package com.nexus.iptv.feature.multiview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import com.nexus.iptv.feature.multiview.domain.MultiviewLayoutEngine
import com.nexus.iptv.feature.multiview.domain.MultiviewLayoutMode
import com.nexus.iptv.feature.multiview.domain.MultiviewSlotState
import com.nexus.iptv.feature.multiview.hardware.MultiviewHardwareCapability

/**
 * Экран режима Multiview — одновременный просмотр 2 или 4 телеканалов (Этап 10, п. 12 ТЗ).
 *
 * Реализует:
 * - Переключение сеток на 2 окна (50/50 или 70/30) и 4 окна (сетка 2×2 или Главное + 3 боковых);
 * - Отображение аппаратных лимитов устройства (ОЗУ, число декодеров, расчётная нагрузка на сеть в Мбит/с);
 * - Выбор основного окна (изменение размеров окон), мгновенное переключение звука (Solo Audio Focus)
 *   и переход выбранного канала в полноэкранный режим в 1 клик.
 */
@Composable
fun MultiviewSectionScreen(
    availableChannels: List<ChannelEntity>,
    hardwareCapability: MultiviewHardwareCapability,
    accentColor: Color,
    onAssignChannelToSlot: (slotIndex: Int, channel: ChannelEntity?) -> Unit,
    onAudioFocusChanged: (slotIndex: Int) -> Unit,
    onExpandChannelFullscreen: (ChannelEntity) -> Unit
) {
    var layoutMode by remember {
        mutableStateOf(
            if (hardwareCapability.maxRecommendedWindows >= 4) MultiviewLayoutMode.QUAD_GRID
            else MultiviewLayoutMode.DUAL_SPLIT
        )
    }
    var primarySlotIndex by remember { mutableIntStateOf(0) }
    var audioFocusSlotIndex by remember { mutableIntStateOf(0) }
    var channelPickerForSlot by remember { mutableStateOf<Int?>(null) }

    val assignedChannels = remember { mutableStateMapOf<Int, ChannelEntity?>() }

    val slotStates = remember(layoutMode, assignedChannels.toMap(), primarySlotIndex, audioFocusSlotIndex) {
        MultiviewLayoutEngine.computeSlotStates(
            layoutMode = layoutMode,
            assignedChannels = assignedChannels,
            primarySlotIndex = primarySlotIndex,
            audioFocusSlotIndex = audioFocusSlotIndex
        )
    }

    val estimatedMbps = remember(slotStates) {
        MultiviewLayoutEngine.estimateRequiredBandwidthMbps(slotStates)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070D))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Верхняя панель управления сеткой и телеметрия ресурсов Multiview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MULTIVIEW — МУЛЬТИЭКРАН (${layoutMode.windowCount} окна)",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${hardwareCapability.recommendationSummary} • Расчётный трафик: ~${"%.1f".format(estimatedMbps)} Мбит/с",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MultiviewLayoutMode.entries.forEach { mode ->
                        val isSelected = mode == layoutMode
                        MvPillButton(
                            text = mode.titleRu,
                            accentColor = if (isSelected) accentColor else Color(0xFF334155),
                            onClick = { layoutMode = mode }
                        )
                    }
                }
            }

            // 2. Адаптивная видеосетка (2 или 4 окна с изменением размеров главного окна)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (layoutMode) {
                    MultiviewLayoutMode.DUAL_SPLIT,
                    MultiviewLayoutMode.DUAL_FOCUS -> {
                        val w0 = slotStates[0].weightFraction
                        val w1 = slotStates[1].weightFraction
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MultiviewWindowTile(
                                slot = slotStates[0],
                                accentColor = accentColor,
                                onSelectAudio = {
                                    audioFocusSlotIndex = 0
                                    onAudioFocusChanged(0)
                                },
                                onMakePrimary = { primarySlotIndex = 0 },
                                onPickChannel = { channelPickerForSlot = 0 },
                                onExpandFullscreen = { slotStates[0].channel?.let(onExpandChannelFullscreen) },
                                modifier = Modifier.weight(w0).fillMaxHeight()
                            )
                            MultiviewWindowTile(
                                slot = slotStates[1],
                                accentColor = accentColor,
                                onSelectAudio = {
                                    audioFocusSlotIndex = 1
                                    onAudioFocusChanged(1)
                                },
                                onMakePrimary = { primarySlotIndex = 1 },
                                onPickChannel = { channelPickerForSlot = 1 },
                                onExpandFullscreen = { slotStates[1].channel?.let(onExpandChannelFullscreen) },
                                modifier = Modifier.weight(w1).fillMaxHeight()
                            )
                        }
                    }

                    MultiviewLayoutMode.QUAD_GRID -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listOf(0, 1).forEach { idx ->
                                    MultiviewWindowTile(
                                        slot = slotStates[idx],
                                        accentColor = accentColor,
                                        onSelectAudio = { audioFocusSlotIndex = idx; onAudioFocusChanged(idx) },
                                        onMakePrimary = { primarySlotIndex = idx },
                                        onPickChannel = { channelPickerForSlot = idx },
                                        onExpandFullscreen = { slotStates[idx].channel?.let(onExpandChannelFullscreen) },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listOf(2, 3).forEach { idx ->
                                    MultiviewWindowTile(
                                        slot = slotStates[idx],
                                        accentColor = accentColor,
                                        onSelectAudio = { audioFocusSlotIndex = idx; onAudioFocusChanged(idx) },
                                        onMakePrimary = { primarySlotIndex = idx },
                                        onPickChannel = { channelPickerForSlot = idx },
                                        onExpandFullscreen = { slotStates[idx].channel?.let(onExpandChannelFullscreen) },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }

                    MultiviewLayoutMode.QUAD_FOCUS -> {
                        val primarySlot = slotStates.firstOrNull { it.isPrimaryWindow } ?: slotStates[0]
                        val sideSlots = slotStates.filter { it.slotIndex != primarySlot.slotIndex }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Крупное основное окно (65% ширины)
                            MultiviewWindowTile(
                                slot = primarySlot,
                                accentColor = accentColor,
                                onSelectAudio = {
                                    audioFocusSlotIndex = primarySlot.slotIndex
                                    onAudioFocusChanged(primarySlot.slotIndex)
                                },
                                onMakePrimary = { primarySlotIndex = primarySlot.slotIndex },
                                onPickChannel = { channelPickerForSlot = primarySlot.slotIndex },
                                onExpandFullscreen = { primarySlot.channel?.let(onExpandChannelFullscreen) },
                                modifier = Modifier.weight(0.65f).fillMaxHeight()
                            )

                            // 3 вспомогательных боковых окна (35% ширины)
                            Column(
                                modifier = Modifier.weight(0.35f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                sideSlots.forEach { side ->
                                    MultiviewWindowTile(
                                        slot = side,
                                        accentColor = accentColor,
                                        onSelectAudio = {
                                            audioFocusSlotIndex = side.slotIndex
                                            onAudioFocusChanged(side.slotIndex)
                                        },
                                        onMakePrimary = { primarySlotIndex = side.slotIndex },
                                        onPickChannel = { channelPickerForSlot = side.slotIndex },
                                        onExpandFullscreen = { side.channel?.let(onExpandChannelFullscreen) },
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Модальное окно выбора канала для назначенного слота
        channelPickerForSlot?.let { targetSlot ->
            SlotChannelPickerDialog(
                slotIndex = targetSlot,
                channels = availableChannels,
                accentColor = accentColor,
                onSelectChannel = { chosen ->
                    assignedChannels[targetSlot] = chosen
                    onAssignChannelToSlot(targetSlot, chosen)
                    if (chosen != null) {
                        audioFocusSlotIndex = targetSlot
                        onAudioFocusChanged(targetSlot)
                    }
                    channelPickerForSlot = null
                },
                onDismiss = { channelPickerForSlot = null }
            )
        }
    }
}

@Composable
private fun MultiviewWindowTile(
    slot: MultiviewSlotState,
    accentColor: Color,
    onSelectAudio: () -> Unit,
    onMakePrimary: () -> Unit,
    onPickChannel: () -> Unit,
    onExpandFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        slot.isAudioActive -> Color(0xFF00E676)
        slot.isPrimaryWindow -> accentColor
        else -> Color(0xFF1E293B)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E1525))
            .border(width = if (slot.isAudioActive || slot.isPrimaryWindow) 2.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .nexusTvFocusable(
                accentColor = accentColor,
                cornerRadius = 14.dp,
                focusedScale = 1.01f,
                onSelect = {
                    if (slot.channel == null) onPickChannel()
                    else onSelectAudio()
                },
                onContextMenu = onPickChannel
            )
            .clickable {
                if (slot.channel == null) onPickChannel()
                else onSelectAudio()
            }
            .padding(14.dp)
    ) {
        if (slot.channel == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Окно #${slot.slotIndex + 1} (Свободно)",
                    color = Color(0xFF94A3B8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                MvPillButton(text = "+ Выбрать канал", accentColor = accentColor, onClick = onPickChannel)
            }
        } else {
            // Верхняя плашка статуса окна (Звук / Главное окно)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (slot.isAudioActive) Color(0xFF00E676).copy(alpha = 0.22f) else Color(0xFF334155))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (slot.isAudioActive) "🔊 ЗВУК АКТИВЕН" else "🔇 MUTE",
                            color = if (slot.isAudioActive) Color(0xFF00E676) else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (slot.isPrimaryWindow) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = "ГЛАВНОЕ", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                Text(
                    text = "#${slot.channel.channelNumber} ${slot.channel.name}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Нижняя панель быстрого управления окном
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!slot.isAudioActive) {
                    MvPillButton(text = "Вкл. звук", accentColor = Color(0xFF00E676), onClick = onSelectAudio)
                }
                if (!slot.isPrimaryWindow) {
                    MvPillButton(text = "Сделать главным", accentColor = accentColor, onClick = onMakePrimary)
                }
                MvPillButton(text = "Канал", accentColor = Color(0xFF475569), onClick = onPickChannel)
                MvPillButton(text = "На весь экран", accentColor = Color(0xFF38BDF8), onClick = onExpandFullscreen)
            }
        }
    }
}

@Composable
private fun SlotChannelPickerDialog(
    slotIndex: Int,
    channels: List<ChannelEntity>,
    accentColor: Color,
    onSelectChannel: (ChannelEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .height(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101829))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Выбор канала для окна #${slotIndex + 1}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                MvPillButton(text = "Очистить слот", accentColor = Color(0xFFEF4444), onClick = { onSelectChannel(null) })
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { "mv_pick_${it.id}" }) { ch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF162035))
                            .nexusTvFocusable(
                                accentColor = accentColor,
                                cornerRadius = 10.dp,
                                onSelect = { onSelectChannel(ch) }
                            )
                            .clickable { onSelectChannel(ch) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "#${ch.channelNumber}  ${ch.name}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(text = ch.categoryName, color = accentColor, fontSize = 12.sp)
                    }
                }
            }

            MvPillButton(text = "Закрыть", accentColor = Color(0xFF475569), onClick = onDismiss)
        }
    }
}

@Composable
private fun MvPillButton(
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.24f))
            .nexusTvFocusable(accentColor = accentColor, cornerRadius = 8.dp, onSelect = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
