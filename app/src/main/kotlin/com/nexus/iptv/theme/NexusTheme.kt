package com.nexus.iptv.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.nexus.iptv.core.model.NexusUiPreferences
import com.nexus.iptv.core.model.ThemeMode

@Composable
fun NexusIptvTheme(preferences: NexusUiPreferences, content: @Composable () -> Unit) {
    val primaryColor = Color(preferences.accentScheme.primaryHex)
    val secondaryColor = Color(preferences.accentScheme.secondaryHex)
    val colorScheme = when (preferences.themeMode) {
        ThemeMode.LIGHT -> lightColorScheme(primary = primaryColor, secondary = secondaryColor, background = Color(0xFFF8FAFC), surface = Color(0xFFFFFFFF))
        ThemeMode.OLED_BLACK -> darkColorScheme(primary = primaryColor, secondary = secondaryColor, background = Color(0xFF000000), surface = Color(0xFF080B11))
        ThemeMode.DARK -> darkColorScheme(primary = primaryColor, secondary = secondaryColor, background = Color(0xFF070A12), surface = Color(0xFF121A2A))
    }
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(density = baseDensity.density * preferences.uiScaleFactor, fontScale = baseDensity.fontScale * preferences.uiScaleFactor)
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
