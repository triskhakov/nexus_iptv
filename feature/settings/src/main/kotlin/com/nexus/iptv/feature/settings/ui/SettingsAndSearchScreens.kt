package com.nexus.iptv.feature.settings.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.common.performance.MemoryBudgetProfile
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import com.nexus.iptv.core.model.ColorAccentScheme
import com.nexus.iptv.core.model.NexusUiPreferences
import com.nexus.iptv.core.model.StartupBehavior
import com.nexus.iptv.core.model.ThemeMode
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import com.nexus.iptv.feature.settings.search.SearchResultCategory
import com.nexus.iptv.feature.settings.search.UniversalSearchHit
import com.nexus.iptv.feature.settings.search.UniversalSmartSearchEngine
import com.nexus.iptv.feature.settings.security.CustomizableRemoteButton
import com.nexus.iptv.feature.settings.security.RemoteCustomAction
import com.nexus.iptv.feature.settings.security.UserProfileConfig

/**
 * Экран Умного глобального поиска по всем разделам (Этап 13, п. 14 и п. 19 ТЗ).
 */
@Composable
fun UniversalSearchScreen(
    channels: List<ChannelEntity>,
    programs: List<EpgProgramEntity>,
    vodItems: List<VodItemEntity>,
    recentQueries: List<String>,
    accentColor: Color,
    onSelectSearchHit: (UniversalSearchHit) -> Unit,
    onSaveSearchQuery: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SearchResultCategory.ALL) }

    val results = remember(query, selectedCategory, channels, programs, vodItems) {
        UniversalSmartSearchEngine.searchAll(
            rawQuery = query,
            filterCategory = selectedCategory,
            channels = channels,
            programs = programs,
            vodItems = vodItems
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Умный глобальный поиск (Каналы, EPG, Фильмы и Сериалы)",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Введите запрос (поддерживается поиск с опечатками и без учёта регистра)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Фильтры разделов
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchResultCategory.entries) { cat ->
                SettingsPillButton(
                    text = cat.titleRu,
                    accentColor = if (selectedCategory == cat) accentColor else Color(0xFF334155),
                    onClick = { selectedCategory = cat }
                )
            }
        }

        // История запросов
        if (query.isEmpty() && recentQueries.isNotEmpty()) {
            Text("История запросов:", color = Color(0xFF94A3B8), fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentQueries) { past ->
                    SettingsPillButton(text = past, accentColor = Color(0xFF1E293B), onClick = { query = past })
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.id }) { hit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141C2B))
                        .nexusTvFocusable(
                            accentColor = accentColor,
                            cornerRadius = 12.dp,
                            onSelect = {
                                onSaveSearchQuery(query)
                                onSelectSearchHit(hit)
                            }
                        )
                        .clickable {
                            onSaveSearchQuery(query)
                            onSelectSearchHit(hit)
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = hit.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = hit.subtitle, color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                    Text(
                        text = hit.category.titleRu,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Экран настроек, профилей, персонализации ПДУ, родительского контроля и оптимизации ОЗУ (Этап 13, п. 13, 15, 16, 18, 19 ТЗ).
 */
@Composable
fun NexusSettingsScreen(
    uiPreferences: NexusUiPreferences,
    memoryBudget: MemoryBudgetProfile,
    profiles: List<UserProfileConfig>,
    remoteBindings: Map<CustomizableRemoteButton, RemoteCustomAction>,
    accentColor: Color,
    onChangeThemeMode: (ThemeMode) -> Unit,
    onChangeAccentScheme: (ColorAccentScheme) -> Unit,
    onChangeSurfaceAlpha: (Float) -> Unit,
    onChangeUiScale: (Float) -> Unit,
    onChangeStartupBehavior: (StartupBehavior) -> Unit,
    onSelectProfile: (UserProfileConfig) -> Unit,
    onCycleRemoteBinding: (CustomizableRemoteButton) -> Unit,
    onClearImageCacheNow: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .padding(24.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = "Настройки NEXUS IPTV, Профили и Оптимизация",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // 1. Система профилей (Profile System)
        item {
            SettingsCard(title = "1. Пользовательские профили (Profile System)", accentColor = accentColor) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    profiles.forEach { profile ->
                        val isActive = profile.id == uiPreferences.activeProfileId
                        SettingsPillButton(
                            text = if (profile.isKidsProfile) "${profile.name} (Детский)" else profile.name,
                            accentColor = if (isActive) accentColor else Color(0xFF334155),
                            onClick = { onSelectProfile(profile) }
                        )
                    }
                }
            }
        }

        // 2. Внешний вид: Тема, Акцент, Прозрачность и Масштаб (п. 13 ТЗ)
        item {
            SettingsCard(title = "2. Оформление, прозрачность и масштаб интерфейса", accentColor = accentColor) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        SettingsPillButton(
                            text = "Тема: ${mode.name}",
                            accentColor = if (uiPreferences.themeMode == mode) accentColor else Color(0xFF334155),
                            onClick = { onChangeThemeMode(mode) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorAccentScheme.entries.forEach { scheme ->
                        SettingsPillButton(
                            text = scheme.title,
                            accentColor = if (uiPreferences.accentScheme == scheme) Color(scheme.primaryHex) else Color(0xFF334155),
                            onClick = { onChangeAccentScheme(scheme) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Прозрачность панелей: ${(uiPreferences.surfaceAlpha * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    SettingsPillButton("70%", accentColor) { onChangeSurfaceAlpha(0.70f) }
                    SettingsPillButton("88%", accentColor) { onChangeSurfaceAlpha(0.88f) }
                    SettingsPillButton("100%", accentColor) { onChangeSurfaceAlpha(1.0f) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Масштаб элементов: ${(uiPreferences.uiScaleFactor * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    SettingsPillButton("90%", accentColor) { onChangeUiScale(0.90f) }
                    SettingsPillButton("100%", accentColor) { onChangeUiScale(1.00f) }
                    SettingsPillButton("115%", accentColor) { onChangeUiScale(1.15f) }
                }
            }
        }

        // 3. Smart Startup — Действие при запуске приложения (п. 15 и п. 19 ТЗ)
        item {
            SettingsCard(title = "3. Поведение при запуске (Smart Startup)", accentColor = accentColor) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StartupBehavior.entries.forEach { behavior ->
                        val label = when (behavior) {
                            StartupBehavior.OPEN_DASHBOARD -> "Главный экран"
                            StartupBehavior.RESUME_LAST_CHANNEL -> "Автозапуск последнего канала"
                            StartupBehavior.OPEN_LIVE_TV_LIST -> "Список Live TV"
                        }
                        SettingsPillButton(
                            text = label,
                            accentColor = if (uiPreferences.startupBehavior == behavior) accentColor else Color(0xFF334155),
                            onClick = { onChangeStartupBehavior(behavior) }
                        )
                    }
                }
            }
        }

        // 4. Настройка кнопок пульта ДУ (Remote Customization — п. 19 ТЗ)
        item {
            SettingsCard(title = "4. Настройка кнопок пульта дистанционного управления", accentColor = accentColor) {
                CustomizableRemoteButton.entries.forEach { button ->
                    val assigned = remoteBindings[button] ?: RemoteCustomAction.TOGGLE_FAVORITE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = button.titleRu, color = Color.White, fontSize = 14.sp)
                        SettingsPillButton(
                            text = assigned.titleRu,
                            accentColor = accentColor,
                            onClick = { onCycleRemoteBinding(button) }
                        )
                    }
                }
            }
        }

        // 5. Оптимизация ОЗУ и кэша (2 GB RAM Ready — п. 18 ТЗ)
        item {
            SettingsCard(title = "5. Производительность и оптимизация памяти устройства", accentColor = accentColor) {
                Text(
                    text = "Обнаружено ОЗУ: ${memoryBudget.totalRamMb} МБ (${if (memoryBudget.isLowMemoryTier) "Режим экономии памяти 2 GB RAM" else "Стандартный профиль"}) • Кэш логотипов: ${memoryBudget.imageMemoryCacheBytes / 1048576} МБ • Размер страницы Paging: ${memoryBudget.pagingPageSize}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
                SettingsPillButton(
                    text = "Очистить кэш изображений и освободить ОЗУ",
                    accentColor = Color(0xFF38BDF8),
                    onClick = onClearImageCacheNow
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121A2A))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SettingsPillButton(
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
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
