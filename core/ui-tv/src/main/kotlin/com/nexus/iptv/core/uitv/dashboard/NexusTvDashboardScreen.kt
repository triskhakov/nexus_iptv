package com.nexus.iptv.core.uitv.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.model.ChannelSummaryItem
import com.nexus.iptv.core.model.ContinueWatchingItem
import com.nexus.iptv.core.model.DashboardUiState
import com.nexus.iptv.core.model.NexusDestination
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable

/**
 * Полноценный экран Android TV / Google TV с боковой D-pad панелью и 7 секциями Главного экрана (п. 4 и п. 13 ТЗ).
 */
@Composable
fun NexusTvDashboardScreen(
    uiState: DashboardUiState,
    selectedDestination: NexusDestination,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelectDestination: (NexusDestination) -> Unit,
    onOpenChannel: (Long) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenVodItem: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.14f),
                        Color(0xFF0A0E17),
                        Color(0xFF05070B)
                    )
                )
            )
    ) {
        // Левая навигационная панель для управления пультом (D-pad Rail)
        TvNavigationRail(
            selectedDestination = selectedDestination,
            accentColor = accentColor,
            surfaceAlpha = surfaceAlpha,
            onSelectDestination = onSelectDestination
        )

        // Основной контент Главного экрана из 7 логических секций (п. 13 ТЗ)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // Заголовок и статус профиля / источников
            item {
                TvHeaderBar(
                    profileName = uiState.activeProfileName,
                    activeSourcesCount = uiState.activeSourcesCount,
                    accentColor = accentColor,
                    onQuickDiagnostics = { onSelectDestination(NexusDestination.NETWORK_DIAGNOSTICS) },
                    onQuickSettings = { onSelectDestination(NexusDestination.SETTINGS) }
                )
            }

            // 1. Продолжить просмотр
            if (uiState.continueWatching.isNotEmpty()) {
                item {
                    TvSectionTitle("1. Продолжить просмотр", accentColor)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.continueWatching, key = { "cw_${it.id}" }) { item ->
                            TvContinueWatchingCard(
                                item = item,
                                accentColor = accentColor,
                                surfaceAlpha = surfaceAlpha,
                                onClick = {
                                    if (item.isLiveChannel) onOpenChannel(item.id)
                                    else onOpenVodItem(item.id)
                                }
                            )
                        }
                    }
                }
            }

            // 2. Последние каналы
            item {
                TvSectionTitle("2. Последние каналы", accentColor)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.recentChannels, key = { "recent_${it.id}" }) { channel ->
                        TvChannelCard(
                            channel = channel,
                            accentColor = accentColor,
                            surfaceAlpha = surfaceAlpha,
                            onSelect = { onOpenChannel(channel.id) }
                        )
                    }
                }
            }

            // 3. Избранное (Unified Favorites из всех источников)
            item {
                TvSectionTitle("3. Избранное", accentColor)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.favoriteChannels, key = { "fav_${it.id}" }) { channel ->
                        TvChannelCard(
                            channel = channel,
                            accentColor = accentColor,
                            surfaceAlpha = surfaceAlpha,
                            onSelect = { onOpenChannel(channel.id) }
                        )
                    }
                }
            }

            // 4. Популярные категории из добавленных источников
            item {
                TvSectionTitle("4. Популярные категории", accentColor)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.popularCategories, key = { "cat_$it" }) { category ->
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF141C2B).copy(alpha = surfaceAlpha))
                                .nexusTvFocusable(
                                    accentColor = accentColor,
                                    onSelect = { onOpenCategory(category) }
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = category,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 5. Программа передач (Сейчас в эфире)
            item {
                TvSectionTitle("5. Программа передач — Сейчас в эфире", accentColor)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.epgHighlights, key = { "epg_${it.id}" }) { channel ->
                        TvChannelCard(
                            channel = channel,
                            accentColor = accentColor,
                            surfaceAlpha = surfaceAlpha,
                            onSelect = { onOpenChannel(channel.id) }
                        )
                    }
                }
            }

            // 6. Фильмы и сериалы (VOD)
            if (uiState.vodRecommendations.isNotEmpty()) {
                item {
                    TvSectionTitle("6. Фильмы и сериалы (VOD)", accentColor)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.vodRecommendations, key = { "vod_${it.id}" }) { vod ->
                            TvContinueWatchingCard(
                                item = vod,
                                accentColor = accentColor,
                                surfaceAlpha = surfaceAlpha,
                                onClick = { onOpenVodItem(vod.id) }
                            )
                        }
                    }
                }
            }

            // 7. Быстрый доступ к настройкам, источникам и диагностике
            item {
                TvSectionTitle("7. Быстрое управление и диагностика", accentColor)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TvQuickActionTile(
                        title = "Менеджер плейлистов",
                        subtitle = "M3U, Xtream Codes, EPG",
                        accentColor = accentColor,
                        onSelect = { onSelectDestination(NexusDestination.SOURCES_MANAGER) }
                    )
                    TvQuickActionTile(
                        title = "Диагностика сети",
                        subtitle = "Тест скорости и декодеров",
                        accentColor = accentColor,
                        onSelect = { onSelectDestination(NexusDestination.NETWORK_DIAGNOSTICS) }
                    )
                    TvQuickActionTile(
                        title = "Мультиэкран (2–4 окна)",
                        subtitle = "Одновременный просмотр",
                        accentColor = accentColor,
                        onSelect = { onSelectDestination(NexusDestination.MULTIVIEW) }
                    )
                    TvQuickActionTile(
                        title = "Настройки и Профили",
                        subtitle = "Темы, ПДУ, Родительский контроль",
                        accentColor = accentColor,
                        onSelect = { onSelectDestination(NexusDestination.SETTINGS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvNavigationRail(
    selectedDestination: NexusDestination,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelectDestination: (NexusDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(Color(0xFF0D131F).copy(alpha = surfaceAlpha))
            .padding(vertical = 24.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "NEXUS IPTV",
            color = accentColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )

        NexusDestination.entries.forEach { destination ->
            val isSelected = destination == selectedDestination
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .nexusTvFocusable(
                        accentColor = accentColor,
                        cornerRadius = 12.dp,
                        focusedScale = 1.02f,
                        onSelect = { onSelectDestination(destination) }
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = destination.titleRu,
                    color = if (isSelected) accentColor else Color(0xFFE2E8F0),
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TvHeaderBar(
    profileName: String,
    activeSourcesCount: Int,
    accentColor: Color,
    onQuickDiagnostics: () -> Unit,
    onQuickSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Профиль: $profileName",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Активных источников IPTV: $activeSourcesCount • Smart Stream Engine активен",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .nexusTvFocusable(accentColor = accentColor, onSelect = onQuickDiagnostics)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = "Проверить источник", color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .nexusTvFocusable(accentColor = accentColor, onSelect = onQuickSettings)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = "Настройки", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TvSectionTitle(title: String, accentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TvChannelCard(
    channel: ChannelSummaryItem,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141C2B).copy(alpha = surfaceAlpha))
            .nexusTvFocusable(accentColor = accentColor, onSelect = onSelect)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${channel.number}  ${channel.name}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Индикатор Channel Health Score (п. 19 ТЗ)
            HealthScoreBadge(score = channel.healthScore)
        }

        Text(
            text = channel.currentProgramTitle ?: "Прямой эфир",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        LinearProgressIndicator(
            progress = { channel.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.12f)
        )

        Text(
            text = "Далее: ${channel.nextProgramTitle ?: "Расписание загружается"}",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvContinueWatchingCard(
    item: ContinueWatchingItem,
    accentColor: Color,
    surfaceAlpha: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF162032).copy(alpha = surfaceAlpha))
            .nexusTvFocusable(accentColor = accentColor, onSelect = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.subtitle,
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { item.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
        Text(
            text = "Осталось ${item.remainingMinutes} мин.",
            color = accentColor,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TvQuickActionTile(
    title: String,
    subtitle: String,
    accentColor: Color,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(235.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF182235))
            .nexusTvFocusable(accentColor = accentColor, onSelect = onSelect)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 13.sp)
    }
}

@Composable
fun HealthScoreBadge(score: Int) {
    val badgeColor = when {
        score >= 85 -> Color(0xFF00E676)
        score >= 60 -> Color(0xFFFFD54F)
        else -> Color(0xFFFF5252)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(badgeColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$score%",
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
