package com.nexus.iptv.feature.vod.ui

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
import com.nexus.iptv.core.database.entity.SeriesEpisodeEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import com.nexus.iptv.feature.vod.domain.VodContentFilter
import com.nexus.iptv.feature.vod.domain.VodDomainEngine
import com.nexus.iptv.feature.vod.domain.VodSortOrder

/**
 * Экран раздела Movies & Series — VOD (Этап 8, п. 11 ТЗ).
 *
 * Реализует:
 * - Переключение вкладок: Все, Фильмы, Сериалы, Продолжить просмотр, ★ Избранное;
 * - Фильтрацию по категориям и жанрам, глобальный поиск и сортировку (по рейтингу, названию, году, истории);
 * - Карточки фильмов и сериалов с постерами, рейтингом, годом, жанром и полосой сохранённого прогресса;
 * - Детальную страницу фильма/сериала с переключением сезонов, списком серий, кнопками «Продолжить с ММ:СС» и «Смотреть сначала».
 */
@Composable
fun VodSectionScreen(
    allVodItems: List<VodItemEntity>,
    categories: List<String>,
    genres: List<String>,
    episodesBySeriesId: Map<Long, List<SeriesEpisodeEntity>>,
    accentColor: Color,
    surfaceAlpha: Float,
    onPlayMovie: (movie: VodItemEntity, startPositionMs: Long) -> Unit,
    onPlayEpisode: (series: VodItemEntity, episode: SeriesEpisodeEntity, startPositionMs: Long) -> Unit,
    onToggleFavorite: (VodItemEntity) -> Unit
) {
    var contentFilter by remember { mutableStateOf(VodContentFilter.ALL) }
    var selectedCategory by remember { mutableStateOf("Все категории") }
    var selectedGenre by remember { mutableStateOf("Все жанры") }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(VodSortOrder.RATING_DESC) }
    var selectedVodDetail by remember { mutableStateOf<VodItemEntity?>(null) }

    val filteredVod = remember(allVodItems, contentFilter, selectedCategory, selectedGenre, searchQuery, sortOrder) {
        VodDomainEngine.filterAndSortVod(
            items = allVodItems,
            contentFilter = contentFilter,
            selectedCategory = selectedCategory,
            selectedGenre = selectedGenre,
            searchQuery = searchQuery,
            sortOrder = sortOrder
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Левая колонка: типы контента, категории и жанры
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0D131F).copy(alpha = surfaceAlpha))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "КИНОЗАЛ VOD",
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                VodContentFilter.entries.forEach { filter ->
                    val isSelected = contentFilter == filter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                            .nexusTvFocusable(
                                accentColor = accentColor,
                                cornerRadius = 10.dp,
                                onSelect = { contentFilter = filter }
                            )
                            .clickable { contentFilter = filter }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = filter.titleRu,
                            color = if (isSelected) accentColor else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("КАТЕГОРИИ", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val fullCategories = listOf("Все категории") + categories
                    items(fullCategories, key = { "vcat_$it" }) { cat ->
                        val isCatSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCatSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                .nexusTvFocusable(
                                    accentColor = accentColor,
                                    cornerRadius = 8.dp,
                                    onSelect = { selectedCategory = cat }
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isCatSelected) accentColor else Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Правая область: Поиск, фильтр по жанрам, сортировка и сетка постеров
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
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
                        label = { Text("Поиск фильмов и сериалов по названию, описанию или жанру (${filteredVod.size})") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    VodPillButton(
                        text = "Сортировка: ${sortOrder.titleRu}",
                        accentColor = accentColor,
                        onClick = {
                            val entries = VodSortOrder.entries
                            sortOrder = entries[(sortOrder.ordinal + 1) % entries.size]
                        }
                    )
                }

                // Лента быстрой фильтрации по жанрам
                if (genres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val allGenres = listOf("Все жанры") + genres
                        items(allGenres, key = { "genre_$it" }) { genre ->
                            val isGenreSelected = selectedGenre == genre
                            VodPillButton(
                                text = genre,
                                accentColor = if (isGenreSelected) accentColor else Color(0xFF334155),
                                onClick = { selectedGenre = genre }
                            )
                        }
                    }
                }

                // Сетка карточек фильмов и сериалов
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 200.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredVod, key = { it.id }) { vod ->
                        VodPosterCard(
                            item = vod,
                            accentColor = accentColor,
                            surfaceAlpha = surfaceAlpha,
                            onSelect = { selectedVodDetail = vod }
                        )
                    }
                }
            }
        }

        // Модальная карточка фильма или сериала (сезоны, серии, продолжить просмотр)
        selectedVodDetail?.let { vodItem ->
            VodDetailsModal(
                vodItem = vodItem,
                episodes = episodesBySeriesId[vodItem.id].orEmpty(),
                accentColor = accentColor,
                onPlayMovie = { startPos ->
                    selectedVodDetail = null
                    onPlayMovie(vodItem, startPos)
                },
                onPlayEpisode = { episode, startPos ->
                    selectedVodDetail = null
                    onPlayEpisode(vodItem, episode, startPos)
                },
                onToggleFavorite = { onToggleFavorite(vodItem) },
                onDismiss = { selectedVodDetail = null }
            )
        }
    }
}

@Composable
private fun VodPosterCard(
    item: VodItemEntity,
    accentColor: Color,
    surfaceAlpha: Float,
    onSelect: () -> Unit
) {
    val progress = VodDomainEngine.computeProgressFraction(item.lastPositionMs, item.durationMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141C2B).copy(alpha = surfaceAlpha))
            .nexusTvFocusable(accentColor = accentColor, cornerRadius = 14.dp, onSelect = onSelect)
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.20f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (item.contentType == "SERIES") "СЕРИАЛ" else "ФИЛЬМ",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (item.rating > 0f) {
                Text(
                    text = "★ ${"%.1f".format(item.rating)}",
                    color = Color(0xFFFFD54F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = listOfNotNull(item.releaseYear?.toString(), item.genre ?: item.categoryName).joinToString(" • "),
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.14f)
            )
            val remainingMin = VodDomainEngine.computeRemainingMinutes(item.lastPositionMs, item.durationMs)
            Text(
                text = "Продолжить (осталось $remainingMin мин.)",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun VodDetailsModal(
    vodItem: VodItemEntity,
    episodes: List<SeriesEpisodeEntity>,
    accentColor: Color,
    onPlayMovie: (startPositionMs: Long) -> Unit,
    onPlayEpisode: (episode: SeriesEpisodeEntity, startPositionMs: Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val seasons = remember(episodes) {
        episodes.map { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeason by remember(seasons) {
        mutableIntStateOf(seasons.firstOrNull() ?: 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(680.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101829))
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vodItem.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = listOfNotNull(
                            if (vodItem.contentType == "SERIES") "Сериал" else "Фильм",
                            vodItem.releaseYear?.toString(),
                            vodItem.genre ?: vodItem.categoryName,
                            if (vodItem.rating > 0f) "Рейтинг ★ ${"%.1f".format(vodItem.rating)}" else null
                        ).joinToString("  •  "),
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                VodPillButton(text = "Закрыть", accentColor = Color(0xFF475569), onClick = onDismiss)
            }

            Text(
                text = vodItem.description ?: "Описание отсутствует в каталоге источника.",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Кнопки управления фильмом + Избранное
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vodItem.contentType == "MOVIE") {
                    if (vodItem.lastPositionMs > 0L) {
                        val resumeMin = vodItem.lastPositionMs / 60_000L
                        VodPillButton(
                            text = "▶ Продолжить с ${resumeMin} мин.",
                            accentColor = Color(0xFF00E676),
                            onClick = { onPlayMovie(vodItem.lastPositionMs) }
                        )
                        VodPillButton(
                            text = "Смотреть сначала",
                            accentColor = accentColor,
                            onClick = { onPlayMovie(0L) }
                        )
                    } else {
                        VodPillButton(
                            text = "▶ Смотреть фильм",
                            accentColor = accentColor,
                            onClick = { onPlayMovie(0L) }
                        )
                    }
                }

                VodPillButton(
                    text = if (vodItem.isFavorite) "Убрать из Избранного" else "★ В Избранное",
                    accentColor = Color(0xFFFFD54F),
                    onClick = onToggleFavorite
                )
            }

            // Секция сезонов и серий для сериалов (п. 11 ТЗ)
            if (vodItem.contentType == "SERIES") {
                if (seasons.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seasons.forEach { seasonNum ->
                            VodPillButton(
                                text = "Сезон $seasonNum",
                                accentColor = if (selectedSeason == seasonNum) accentColor else Color(0xFF334155),
                                onClick = { selectedSeason = seasonNum }
                            )
                        }
                    }

                    val seasonEpisodes = episodes.filter { it.seasonNumber == selectedSeason }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(seasonEpisodes, key = { it.id }) { ep ->
                            val epProgress = VodDomainEngine.computeProgressFraction(ep.lastPositionMs, ep.durationMs)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF162035))
                                    .nexusTvFocusable(
                                        accentColor = accentColor,
                                        cornerRadius = 10.dp,
                                        onSelect = { onPlayEpisode(ep, ep.lastPositionMs) }
                                    )
                                    .clickable { onPlayEpisode(ep, ep.lastPositionMs) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Серия ${ep.episodeNumber}. ${ep.title}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (epProgress > 0f) {
                                        LinearProgressIndicator(
                                            progress = { epProgress },
                                            modifier = Modifier
                                                .fillMaxWidth(0.6f)
                                                .padding(top = 4.dp)
                                                .height(3.dp),
                                            color = accentColor,
                                            trackColor = Color.White.copy(alpha = 0.14f)
                                        )
                                    }
                                }
                                Text(
                                    text = if (ep.lastPositionMs > 0L) "Продолжить ▶" else "Смотреть ▶",
                                    color = accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodPillButton(
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
