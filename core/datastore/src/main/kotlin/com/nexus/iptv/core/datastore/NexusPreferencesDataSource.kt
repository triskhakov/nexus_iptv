package com.nexus.iptv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexus.iptv.core.model.ColorAccentScheme
import com.nexus.iptv.core.model.NexusUiPreferences
import com.nexus.iptv.core.model.StartupBehavior
import com.nexus.iptv.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_iptv_ui_preferences")

@Singleton
class NexusPreferencesDataSource @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_SCHEME = stringPreferencesKey("accent_scheme")
        val SURFACE_ALPHA = floatPreferencesKey("surface_alpha")
        val UI_SCALE_FACTOR = floatPreferencesKey("ui_scale_factor")
        val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
        val STARTUP_BEHAVIOR = stringPreferencesKey("startup_behavior")
        val LAST_PLAYED_CHANNEL_ID = longPreferencesKey("last_played_channel_id")
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val SETTINGS_PIN_ENABLED = booleanPreferencesKey("settings_pin_enabled")
    }

    val uiPreferences: Flow<NexusUiPreferences> = context.nexusDataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { prefs ->
            NexusUiPreferences(
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                accentScheme = prefs[Keys.ACCENT_SCHEME]?.let { runCatching { ColorAccentScheme.valueOf(it) }.getOrNull() } ?: ColorAccentScheme.NEXUS_CYAN,
                surfaceAlpha = (prefs[Keys.SURFACE_ALPHA] ?: 0.88f).coerceIn(0.50f, 1.0f),
                uiScaleFactor = (prefs[Keys.UI_SCALE_FACTOR] ?: 1.0f).coerceIn(0.85f, 1.30f),
                customWallpaperUri = prefs[Keys.CUSTOM_WALLPAPER_URI],
                startupBehavior = prefs[Keys.STARTUP_BEHAVIOR]?.let { runCatching { StartupBehavior.valueOf(it) }.getOrNull() } ?: StartupBehavior.OPEN_DASHBOARD,
                lastPlayedChannelId = prefs[Keys.LAST_PLAYED_CHANNEL_ID],
                activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: 1L,
                isSettingsPinEnabled = prefs[Keys.SETTINGS_PIN_ENABLED] ?: false
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) { context.nexusDataStore.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setAccentScheme(scheme: ColorAccentScheme) { context.nexusDataStore.edit { it[Keys.ACCENT_SCHEME] = scheme.name } }
}
