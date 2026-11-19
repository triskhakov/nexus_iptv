package com.nexus.iptv.feature.livetv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.uitv.dashboard.HealthScoreBadge
import com.nexus.iptv.core.uitv.focus.RemoteAction
import com.nexus.iptv.core.uitv.focus.TvRemoteKeyMapper
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import com.nexus.iptv.feature.livetv.domain.ChannelSortMode
import com.nexus.iptv.feature.livetv.domain.LiveChannelUiModel
import com.nexus.iptv.feature.livetv.domain.LiveTvViewMode
import com.nexus.iptv.feature.livetv.domain.LiveTvZappingEngine
import com.nexus.iptv.feature.livetv.domain.NumericKeypadChannelBuffer

/**
 * Профессиональный раздел прямого телевидения — Live TV (Этап 6, п. 6 и п. 19 ТЗ).
 *
 * Реализует:
 * - 3 режима отображения: Список (LIST), Сеточный режим (GRID), Компактный режим (COMPACT);
 * - Группировку по категориям, фильтр «Избранное», быстрый поиск и 4 режима сортировки;
 * - Полноэкранный режим просмотра с переключением каналов без выхода из Fullscreen:
 *   - Кнопками CH+ / CH- на пульте ДУ;
 *   - Прозрачным боковым оверлеем каналов поверх эфира;
 *   - Панелью Quick Switch (мгновенный возврат к последним просмотренным каналам);
 *   - Набором номера канала цифровыми кнопками пульта (0..9);
 * - Скрытие каналов, добавление в избранное и родительскую PIN-блокировку каналов.
 */
@Composable
fun LiveTvSectionScreen(
    allChannels: List<LiveChannelUiModel>,
    categories: List<String>,
    activeChannel: LiveChannelUiModel?,
    isFullscreenPlayback: Boolean,
    parentalPinCode: String = "0000",
    accentColor: Color,
    surfaceAlpha: Float,
    onSelectChannelForPlayback: (LiveChannelUiModel) -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onToggleFavorite: (LiveChannelUiModel) -> Unit,
    onHideChannel: (LiveChannelUiModel) -> Unit,
    onToggleLockChannel: (LiveChannelUiModel) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Все каналы") }
    var searchQuery by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(LiveTvViewMode.LIST) }
    var sortMode by remember { mutableStateOf(ChannelSortMode.CUSTOM_AND_NUMBER) }

    var showInPlayerChannelDrawer by remember { mutableStateOf(false) }
    var showQuickSwitchBar by remember { mutableStateOf(false) }
    var contextMenuChannel by remember { mutableStateOf<LiveChannelUiModel?>(null) }
    var pendingPinUnlockChannel by remember { mutableStateOf<LiveChannelUiModel?>(null) }
    var dialedNumberBanner by remember { mutableStateOf<Int?>(null) }

    val recentHistoryIds = remember { mutableStateListOf<Long>() }
    val keypadBuffer = remember { NumericKeypadChannelBuffer() }

    val filteredChannels = remember(allChannels, selectedCategory, searchQuery, onlyFavorites, sortMode) {
        LiveTvZappingEngine.filterAndSortChannels(
            channels = allChannels,
            selectedCategory = selectedCategory,
            searchQuery = searchQuery,
            onlyFavorites = onlyFavorites,
            sortMode = sortMode
        )
    }

    val channelsById = remember(allChannels) { allChannels.associateBy { it.id } }
    val quickSwitchQueue = remember(recentHistoryIds.toList(), channelsById, activeChannel?.id) {
        LiveTvZappingEngine.buildQuickSwitchQueue(
            recentHistoryIds = recentHistoryIds,
            allChannelsById = channelsById,
            activeChannelId = activeChannel?.id
        )
    }

    fun requestChannelLaunch(target: LiveChannelUiModel) {
        if (target.isLocked) {
            pendingPinUnlockChannel = target
        } else {
            recentHistoryIds.remove(target.id)
            recentHistoryIds.add(0, target.id)
            onSelectChannelForPlayback(target)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                // 1. Обработка цифровых клавиш пульта 0..9 для прямого набора номера канала
                if (native.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                    val digit = native.keyCode - KeyEvent.KEYCODE_0
                    val currentNum = keypadBuffer.appendDigit(digit)
                    dialedNumberBanner = currentNum
                    allChannels.firstOrNull { it.number == currentNum }?.let { matched ->
                        requestChannelLaunch(matched)
                    }
                    return@onPreviewKeyEvent true
                }

                // 2. Быстрое переключение каналов в полноэкранном режиме без выхода из эфира (п. 6 ТЗ)
                if (isFullscreenPlayback && activeChannel != null) {
                    when (TvRemoteKeyMapper.mapKeyEvent(native.keyCode, native.action, native.repeatCount)) {
                        RemoteAction.CHANNEL_UP -> {
                            LiveTvZappingEngine.resolveAdjacentChannel(filteredChannels, activeChannel.id, +1)
                                ?.let { requestChannelLaunch(it) }
                            true
                        }
                        RemoteAction.CHANNEL_DOWN -> {
                            LiveTvZappingEngine.resolveAdjacentChannel(filteredChannels, activeChannel.id, -1)
                                ?.let { requestChannelLaunch(it) }
                            true
                        }
                        RemoteAction.OPEN_SIDE_MENU -> {
                            showInPlayerChannelDrawer = !showInPlayerChannelDrawer
                            true
                        }
                        RemoteAction.SHOW_EPG_INFO -> {
                            showQuickSwitchBar = !showQuickSwitchBar
                            true
                        }
                        RemoteAction.NAVIGATE_BACK -> {
                            when {
                                showInPlayerChannelDrawer -> { showInPlayerChannelDrawer = false; true }
                                showQuickSwitchBar -> { showQuickSwitchBar = false; true }
                                else -> { onToggleFullscreen(false); true }
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (isFullscreenPlayback && activeChannel != null) {
            // ПОЛНОЭКРАННЫЙ РЕЖИМ С ПЕРЕКЛЮЧЕНИЕМ КАНАЛОВ БЕЗ ВЫХОДА ИЗ ЭФИРА
            FullscreenZappingOverlay(
                activeChannel = activeChannel,
                filteredChannels = filteredChannels,
                quickSwitchChannels = quickSwitchQueue,
                showChannelDrawer = showInPlayerChannelDrawer,
                showQuickSwitch = showQuickSwitchBar,
                dialedNumber = dialedNumberBanner,
                accentColor = accentColor,
                surfaceAlpha = surfaceAlpha,
                onSelectChannel = { ch -> requestChannelLaunch(ch) },
                onStepChannel = { step ->
                    LiveTvZappingEngine.resolveAdjacentChannel(filteredChannels, activeChannel.id, step)
                        ?.let { requestChannelLaunch(it) }
                },
                onToggleDrawer = { showInPlayerChannelDrawer = !showInPlayerChannelDrawer },
                onToggleQuickSwitch = { showQuickSwitchBar = !showQuickSwitchBar },
                onExitFullscreen = { onToggleFullscreen(false) }
            )
        } else {
            // ОСНОВНОЙ ЭКРАН LIVE TV (Категории + Панель фильтрации/режимов + Список/Сетка/Компактный вид)
            Row(modifier = Modifier.fillMaxSize()) {
                // Боковая колонка категорий
                CategoriesSidebar(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onlyFavorites = onlyFavorites,
                    accentColor = accentColor,
                    surfaceAlpha = surfaceAlpha,
                    onSelectCategory = {
                        selectedCategory = it
                        onlyFavorites = false
                    },
                    onSelectFavoritesFilter = { onlyFavorites = !onlyFavorites }
                )

                // Правая рабочая область: поиск, переключатели режимов (Список/Сетка/Компактный) и контент
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LiveTvToolbar(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        viewMode = viewMode,
                        onCycleViewMode = {
                            viewMode = when (viewMode) {
                                LiveTvViewMode.LIST -> LiveTvViewMode.GRID
                                LiveTvViewMode.GRID -> LiveTvViewMode.COMPACT
                                LiveTvViewMode.COMPACT -> LiveTvViewMode.LIST
                            }
                        },
                        sortMode = sortMode,
                        onCycleSortMode = {
                            val entries = ChannelSortMode.entries
                            sortMode = entries[(sortMode.ordinal + 1) % entries.size]
                        },
                        totalCount = filteredChannels.size,
                        activeChannel = activeChannel,
                        accentColor = accentColor,
                        onReturnToFullscreen = { if (activeChannel != null) onToggleFullscreen(true) }
                    )

                    when (viewMode) {
                        LiveTvViewMode.LIST -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredChannels, key = { it.id }) { channel ->
                                    ChannelListRow(
                                        channel = channel,
                                        isPlaying = activeChannel?.id == channel.id,
                                        accentColor = accentColor,
                                        surfaceAlpha = surfaceAlpha,
                                        onSelect = {
                                            requestChannelLaunch(channel)
                                            onToggleFullscreen(true)
                                        },
                                        onContextMenu = { contextMenuChannel = channel }
                                    )
                                }
                            }
                        }

                        LiveTvViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 230.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredChannels, key = { it.id }) { channel ->
                                    ChannelGridCard(
                                        channel = channel,
                                        isPlaying = activeChannel?.id == channel.id,
                                        accentColor = accentColor,
                                        surfaceAlpha = surfaceAlpha,
                                        onSelect = {
                                            requestChannelLaunch(channel)
                                            onToggleFullscreen(true)
                                        },
                                        onContextMenu = { contextMenuChannel = channel }
                                    )
                                }
                            }
                        }

                        LiveTvViewMode.COMPACT -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredChannels, key = { it.id }) { channel ->
                                    ChannelCompactRow(
                                        channel = channel,
                                        isPlaying = activeChannel?.id == channel.id,
                                        accentColor = accentColor,
                                        onSelect = {
                                            requestChannelLaunch(channel)
                                            onToggleFullscreen(true)
                                        },
                                        onContextMenu = { contextMenuChannel = channel }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Контекстное меню канала (Избранное, Скрыть, Заблокировать PIN-кодом)
        contextMenuChannel?.let { channel ->
            ChannelContextMenuDialog(
                channel = channel,
                accentColor = accentColor,
                onToggleFavorite = {
                    onToggleFavorite(channel)
                    contextMenuChannel = null
                },
                onHideChannel = {
                    onHideChannel(channel)
                    contextMenuChannel = null
                },
                onToggleLock = {
                    onToggleLockChannel(channel)
                    contextMenuChannel = null
                },
                onDismiss = { contextMenuChannel = null }
            )
        }

        // Модальное окно ввода PIN-кода для защищённых каналов (Родительский контроль — п. 6 и п. 16 ТЗ)
        pendingPinUnlockChannel?.let { lockedChannel ->
            ParentalPinUnlockDialog(
                channelName = lockedChannel.name,
                expectedPin = parentalPinCode,
                accentColor = accentColor,
                onUnlocked = {
                    pendingPinUnlockChannel = null
                    recentHistoryIds.remove(lockedChannel.id)
                    recentHistoryIds.add(0, lockedChannel.id)
                    onSelectChannelForPlayback(lockedChannel)
                    onToggleFullscreen(true)
                },
                onCancel = { pendingPinUnlockChannel = null }
            )
        }
    }
}

@Composable
private fun CategoriesSidebar(
    categories: List<String>,
    selectedCategory: String,
    onlyFavorites: Boolean,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelectCategory: (String) -> Unit,
    onSelectFavoritesFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Color(0xFF0D131F).copy(alpha = surfaceAlpha))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "КАТЕГОРИИ ТВ",
            color = accentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Быстрый фильтр «★ Избранное»
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (onlyFavorites) accentColor.copy(alpha = 0.24f) else Color(0xFF151F32))
                .nexusTvFocusable(accentColor = accentColor, cornerRadius = 10.dp, onSelect = onSelectFavoritesFilter)
                .clickable(onClick = onSelectFavoritesFilter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "★ Избранные каналы",
                color = if (onlyFavorites) accentColor else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(categories, key = { it }) { category ->
                val isSelected = !onlyFavorites && category == selectedCategory
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.20f) else Color.Transparent)
                        .nexusTvFocusable(
                            accentColor = accentColor,
                            cornerRadius = 10.dp,
                            onSelect = { onSelectCategory(category) }
                        )
                        .clickable { onSelectCategory(category) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) accentColor else Color(0xFFCBD5E1),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    viewMode: LiveTvViewMode,
    onCycleViewMode: () -> Unit,
    sortMode: ChannelSortMode,
    onCycleSortMode: () -> Unit,
    totalCount: Int,
    activeChannel: LiveChannelUiModel?,
    accentColor: Color,
    onReturnToFullscreen: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Быстрый поиск по каналам, номеру или текущей передаче ($totalCount)") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        ToolbarPillButton(
            text = "Вид: ${viewMode.titleRu}",
            accentColor = accentColor,
            onClick = onCycleViewMode
        )

        ToolbarPillButton(
            text = "Сортировка: ${sortMode.titleRu}",
            accentColor = accentColor,
            onClick = onCycleSortMode
        )

        if (activeChannel != null) {
            ToolbarPillButton(
                text = "На весь экран (${activeChannel.name})",
                accentColor = Color(0xFF00E676),
                onClick = onReturnToFullscreen
            )
        }
    }
}

@Composable
private fun ChannelListRow(
    channel: LiveChannelUiModel,
    isPlaying: Boolean,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelect: () -> Unit,
    onContextMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPlaying) accentColor.copy(alpha = 0.16f)
                else Color(0xFF131B2A).copy(alpha = surfaceAlpha)
            )
            .nexusTvFocusable(
                accentColor = accentColor,
                cornerRadius = 12.dp,
                onSelect = onSelect,
                onContextMenu = onContextMenu
            )
            .clickable(onClick = onSelect)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${channel.number}",
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isFavorite) {
                        Text(text = "★", color = Color(0xFFFFD54F), fontSize = 15.sp)
                    }
                    if (channel.isLocked) {
                        Text(text = "[PIN]", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (channel.hasCatchup) {
                        Text(text = "[Архив]", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = "Сейчас: ${channel.currentProgramTitle ?: "Прямой эфир"}  •  Далее: ${channel.nextProgramTitle ?: "—"}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                LinearProgressIndicator(
                    progress = { channel.currentProgramProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(3.dp),
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HealthScoreBadge(score = channel.healthScore)
            ToolbarPillButton(text = "Опции", accentColor = Color(0xFF475569), onClick = onContextMenu)
        }
    }
}

@Composable
private fun ChannelGridCard(
    channel: LiveChannelUiModel,
    isPlaying: Boolean,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelect: () -> Unit,
    onContextMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isPlaying) accentColor.copy(alpha = 0.18f)
                else Color(0xFF141C2B).copy(alpha = surfaceAlpha)
            )
            .nexusTvFocusable(
                accentColor = accentColor,
                cornerRadius = 14.dp,
                onSelect = onSelect,
                onContextMenu = onContextMenu
            )
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${channel.number} ${channel.name}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            HealthScoreBadge(score = channel.healthScore)
        }

        Text(
            text = channel.currentProgramTitle ?: "Прямой эфир",
            color = accentColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        LinearProgressIndicator(
            progress = { channel.currentProgramProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.12f)
        )

        Text(
            text = "Далее: ${channel.nextProgramTitle ?: "—"}",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelCompactRow(
    channel: LiveChannelUiModel,
    isPlaying: Boolean,
    accentColor: Color,
    onSelect: () -> Unit,
    onContextMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) accentColor.copy(alpha = 0.20f) else Color(0xFF101725))
            .nexusTvFocusable(
                accentColor = accentColor,
                cornerRadius = 8.dp,
                focusedScale = 1.01f,
                onSelect = onSelect,
                onContextMenu = onContextMenu
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${channel.number}. ${channel.name}",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = channel.currentProgramTitle ?: "Прямой эфир",
            color = accentColor,
            fontSize = 13.sp,
            modifier = Modifier.weight(0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        HealthScoreBadge(score = channel.healthScore)
    }
}

/**
 * Полноэкранный оверлей быстрого переключения каналов (Zapping + Quick Switch) без остановки эфира (п. 6 и п. 19 ТЗ).
 */
@Composable
private fun FullscreenZappingOverlay(
    activeChannel: LiveChannelUiModel,
    filteredChannels: List<LiveChannelUiModel>,
    quickSwitchChannels: List<LiveChannelUiModel>,
    showChannelDrawer: Boolean,
    showQuickSwitch: Boolean,
    dialedNumber: Int?,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelectChannel: (LiveChannelUiModel) -> Unit,
    onStepChannel: (Int) -> Unit,
    onToggleDrawer: () -> Unit,
    onToggleQuickSwitch: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Баннер набора номера канала с пульта ДУ (правый верхний угол)
        if (dialedNumber != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(28.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE60D1526))
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Канал #$dialedNumber",
                    color = accentColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Прозрачный левый список каналов поверх видеопотока (как в TiviMate)
        if (showChannelDrawer) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(Color(0xE6090E1A))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "БЫСТРЫЙ СПИСОК КАНАЛОВ",
                    color = accentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredChannels, key = { "ov_${it.id}" }) { ch ->
                        val isCurrent = ch.id == activeChannel.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) accentColor.copy(alpha = 0.25f) else Color(0xFF141D2F))
                                .nexusTvFocusable(
                                    accentColor = accentColor,
                                    cornerRadius = 10.dp,
                                    onSelect = { onSelectChannel(ch) }
                                )
                                .clickable { onSelectChannel(ch) }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "#${ch.number}  ${ch.name}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = ch.currentProgramTitle ?: "Прямой эфир",
                                color = accentColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Нижняя панель управления полноэкранным эфиром + лента Quick Switch
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xD9060912).copy(alpha = surfaceAlpha))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showQuickSwitch && quickSwitchChannels.isNotEmpty()) {
                Text(
                    text = "Quick Switch — Последние каналы:",
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(quickSwitchChannels, key = { "qs_${it.id}" }) { recentCh ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF172236))
                                .nexusTvFocusable(
                                    accentColor = accentColor,
                                    cornerRadius = 10.dp,
                                    onSelect = { onSelectChannel(recentCh) }
                                )
                                .clickable { onSelectChannel(recentCh) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "#${recentCh.number} ${recentCh.name}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${activeChannel.number}  ${activeChannel.name}",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Сейчас: ${activeChannel.currentProgramTitle ?: "Прямой эфир"}  •  Далее: ${activeChannel.nextProgramTitle ?: "—"}",
                        color = accentColor,
                        fontSize = 14.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolbarPillButton("CH- Пред.", accentColor) { onStepChannel(-1) }
                    ToolbarPillButton("CH+ След.", accentColor) { onStepChannel(+1) }
                    ToolbarPillButton("Список каналов", accentColor, onToggleDrawer)
                    ToolbarPillButton("Quick Switch", accentColor, onToggleQuickSwitch)
                    ToolbarPillButton("Свернуть", Color(0xFF64748B), onExitFullscreen)
                }
            }
        }
    }
}

@Composable
private fun ChannelContextMenuDialog(
    channel: LiveChannelUiModel,
    accentColor: Color,
    onToggleFavorite: () -> Unit,
    onHideChannel: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101829))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "#${channel.number} ${channel.name}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            ToolbarPillButton(
                text = if (channel.isFavorite) "Убрать из Избранного" else "★ Добавить в Избранное",
                accentColor = accentColor,
                onClick = onToggleFavorite
            )
            ToolbarPillButton(
                text = if (channel.isLocked) "Снять PIN-блокировку" else "Заблокировать канал (PIN)",
                accentColor = Color(0xFFF59E0B),
                onClick = onToggleLock
            )
            ToolbarPillButton(
                text = "Скрыть канал из списка",
                accentColor = Color(0xFFEF4444),
                onClick = onHideChannel
            )
            ToolbarPillButton(
                text = "Закрыть",
                accentColor = Color(0xFF475569),
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun ParentalPinUnlockDialog(
    channelName: String,
    expectedPin: String,
    accentColor: Color,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isPinError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121A2B))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Родительский контроль",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Канал «$channelName» защищён PIN-кодом",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            OutlinedTextField(
                value = enteredPin,
                onValueChange = {
                    enteredPin = it
                    isPinError = false
                },
                label = { Text("Введите PIN-код") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isPinError) {
                Text(text = "Неверный PIN-код", color = Color(0xFFEF4444), fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolbarPillButton(
                    text = "Разблокировать",
                    accentColor = accentColor,
                    onClick = {
                        if (enteredPin == expectedPin) onUnlocked()
                        else isPinError = true
                    }
                )
                ToolbarPillButton(
                    text = "Отмена",
                    accentColor = Color(0xFF475569),
                    onClick = onCancel
                )
            }
        }
    }
}

@Composable
private fun ToolbarPillButton(
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
