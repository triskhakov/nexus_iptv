package com.nexus.iptv.core.model

import kotlinx.serialization.Serializable

enum class DeviceFormFactor { TV_DPAD, MOBILE_PORTRAIT, TABLET_OR_LANDSCAPE }
enum class ThemeMode { DARK, LIGHT, OLED_BLACK }

enum class ColorAccentScheme(val title: String, val primaryHex: Long, val secondaryHex: Long) {
    NEXUS_CYAN("Nexus Cyan", 0xFF00E5FF, 0xFF2979FF),
    EMERALD_MATRIX("Emerald Pro", 0xFF00E676, 0xFF00BFA5),
    ROYAL_AMETHYST("Royal Amethyst", 0xFFB388FF, 0xFF7C4DFF),
    SOLAR_GOLD("Solar Gold", 0xFFFFD54F, 0xFFFF8F00)
}

enum class StartupBehavior { OPEN_DASHBOARD, RESUME_LAST_CHANNEL, OPEN_LIVE_TV_LIST }

@Serializable
data class NexusUiPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentScheme: ColorAccentScheme = ColorAccentScheme.NEXUS_CYAN,
    val surfaceAlpha: Float = 0.88f,
    val uiScaleFactor: Float = 1.0f,
    val customWallpaperUri: String? = null,
    val startupBehavior: StartupBehavior = StartupBehavior.OPEN_DASHBOARD,
    val lastPlayedChannelId: Long? = null,
    val activeProfileId: Long = 1L,
    val isSettingsPinEnabled: Boolean = false
)

enum class NexusDestination(val route: String, val titleRu: String) {
    DASHBOARD("dashboard", "Главная"),
    LIVE_TV("livetv", "Телевидение"),
    EPG_GUIDE("epg", "Телепрограмма"),
    VOD_MOVIES("vod", "Фильмы и сериалы"),
    MULTIVIEW("multiview", "Мультиэкран"),
    DVR_RECORDINGS("dvr", "Записи и Архив"),
    GLOBAL_SEARCH("search", "Умный поиск"),
    SOURCES_MANAGER("sources", "Источники IPTV"),
    NETWORK_DIAGNOSTICS("diagnostics", "Диагностика сети"),
    SETTINGS("settings", "Настройки")
}

data class ChannelSummaryItem(
    val id: Long,
    val number: Int,
    val name: String,
    val category: String,
    val logoUrl: String?,
    val currentProgramTitle: String?,
    val nextProgramTitle: String?,
    val progressFraction: Float,
    val isFavorite: Boolean,
    val healthScore: Int,
    val hasCatchup: Boolean
)

data class ContinueWatchingItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val posterUrl: String?,
    val progressFraction: Float,
    val remainingMinutes: Int,
    val isLiveChannel: Boolean
)

data class DashboardUiState(
    val activeProfileName: String = "Основной профиль",
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val recentChannels: List<ChannelSummaryItem> = emptyList(),
    val favoriteChannels: List<ChannelSummaryItem> = emptyList(),
    val popularCategories: List<String> = emptyList(),
    val epgHighlights: List<ChannelSummaryItem> = emptyList(),
    val vodRecommendations: List<ContinueWatchingItem> = emptyList(),
    val activeSourcesCount: Int = 0,
    val isSyncingInBackground: Boolean = false
)
