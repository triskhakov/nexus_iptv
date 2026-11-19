package com.nexus.iptv.core.uimobile.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.model.ChannelSummaryItem
import com.nexus.iptv.core.model.DashboardUiState
import com.nexus.iptv.core.model.NexusDestination

/**
 * Адаптивный сенсорный экран для смартфонов и планшетов (портретный и альбомный режимы, п. 4 и п. 13 ТЗ).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusMobileDashboardScreen(
    uiState: DashboardUiState,
    isTabletOrLandscape: Boolean,
    selectedDestination: NexusDestination,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelectDestination: (NexusDestination) -> Unit,
    onOpenChannel: (Long) -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val primaryNavItems = listOf(
        NexusDestination.DASHBOARD,
        NexusDestination.LIVE_TV,
        NexusDestination.EPG_GUIDE,
        NexusDestination.VOD_MOVIES,
        NexusDestination.SETTINGS
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NEXUS IPTV",
                            color = accentColor,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${uiState.activeProfileName} • Источников: ${uiState.activeSourcesCount}",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .clickable { onSelectDestination(NexusDestination.GLOBAL_SEARCH) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Поиск", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0F1A)
                )
            )
        },
        bottomBar = {
            if (!isTabletOrLandscape) {
                NavigationBar(containerColor = Color(0xFF0D131F)) {
                    primaryNavItems.forEach { dest ->
                        NavigationBarItem(
                            selected = selectedDestination == dest,
                            onClick = { onSelectDestination(dest) },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .width(8.dp)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedDestination == dest) accentColor else Color.Gray)
                                )
                            },
                            label = { Text(dest.titleRu, maxLines = 1, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF070A12)
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTabletOrLandscape) {
                NavigationRail(containerColor = Color(0xFF0D131F)) {
                    primaryNavItems.forEach { dest ->
                        NavigationRailItem(
                            selected = selectedDestination == dest,
                            onClick = { onSelectDestination(dest) },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .width(8.dp)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedDestination == dest) accentColor else Color.Gray)
                                )
                            },
                            label = { Text(dest.titleRu, fontSize = 11.sp) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    MobileSectionHeader("Продолжить просмотр и Последние каналы", accentColor)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.recentChannels, key = { "m_rec_${it.id}" }) { ch ->
                            MobileChannelCard(
                                channel = ch,
                                accentColor = accentColor,
                                surfaceAlpha = surfaceAlpha,
                                onClick = { onOpenChannel(ch.id) }
                            )
                        }
                    }
                }

                item {
                    MobileSectionHeader("Избранные каналы", accentColor)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.favoriteChannels, key = { "m_fav_${it.id}" }) { ch ->
                            MobileChannelCard(
                                channel = ch,
                                accentColor = accentColor,
                                surfaceAlpha = surfaceAlpha,
                                onClick = { onOpenChannel(ch.id) }
                            )
                        }
                    }
                }

                item {
                    MobileSectionHeader("Категории источников", accentColor)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.popularCategories, key = { "m_cat_$it" }) { category ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF151E2E).copy(alpha = surfaceAlpha))
                                    .clickable { onOpenCategory(category) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(category, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    MobileSectionHeader("Быстрые инструменты", accentColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF162032))
                                .clickable { onSelectDestination(NexusDestination.SOURCES_MANAGER) }
                                .padding(14.dp)
                        ) {
                            Text("Источники IPTV", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF162032))
                                .clickable { onSelectDestination(NexusDestination.NETWORK_DIAGNOSTICS) }
                                .padding(14.dp)
                        ) {
                            Text("Диагностика сети", color = accentColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSectionHeader(title: String, accentColor: Color) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun MobileChannelCard(
    channel: ChannelSummaryItem,
    accentColor: Color,
    surfaceAlpha: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141C2B).copy(alpha = surfaceAlpha))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "${channel.number}. ${channel.name}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = channel.currentProgramTitle ?: "Прямой эфир",
            color = accentColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { channel.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
    }
}
