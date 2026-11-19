package com.nexus.iptv.feature.settings

import android.content.ComponentCallbacks2
import com.nexus.iptv.core.common.performance.DeviceResourceBudgetOptimizer
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import com.nexus.iptv.feature.settings.search.SearchResultCategory
import com.nexus.iptv.feature.settings.search.UniversalSmartSearchEngine
import com.nexus.iptv.feature.settings.security.SettingsSecuritySessionGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage13And14OptimizationAndSearchTest {

    @Test
    fun `DeviceResourceBudgetOptimizer enforces strict memory limits on 2GB RAM TV Boxes`() {
        val lowRamProfile = DeviceResourceBudgetOptimizer.computeBudgetProfile(
            totalRamMb = 2048L,
            isLowRamDeviceFlag = false
        )
        assertTrue(lowRamProfile.isLowMemoryTier)
        assertEquals(24L * 1024L * 1024L, lowRamProfile.imageMemoryCacheBytes)
        assertEquals(40, lowRamProfile.pagingPageSize)

        val flagshipProfile = DeviceResourceBudgetOptimizer.computeBudgetProfile(
            totalRamMb = 6144L,
            isLowRamDeviceFlag = false
        )
        assertFalse(flagshipProfile.isLowMemoryTier)
        assertEquals(96L * 1024L * 1024L, flagshipProfile.imageMemoryCacheBytes)
        assertEquals(100, flagshipProfile.pagingPageSize)

        assertTrue(DeviceResourceBudgetOptimizer.shouldEvictNonEssentialCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertFalse(DeviceResourceBudgetOptimizer.shouldEvictNonEssentialCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
    }

    @Test
    fun `UniversalSmartSearchEngine finds channels, EPG and VOD even with spelling typos`() {
        val channels = listOf(
            ChannelEntity(
                id = 1L, sourceId = 1L, channelNumber = 10, name = "Discovery Science HD",
                normalizedName = "discovery science hd", categoryName = "Познавательные",
                streamUrl = "http://s/1", unifiedGroupKey = "discovery_science", isFavorite = true
            )
        )
        val programs = listOf(
            EpgProgramEntity(
                id = 50L, sourceId = 1L, epgChannelId = "disc",
                title = "Квантовая механика Вселенной", description = "Научный фильм",
                startEpochMs = 1000L, endEpochMs = 5000L
            )
        )
        val vod = listOf(
            VodItemEntity(
                id = 99L, sourceId = 1L, contentType = "MOVIE", title = "Интерстеллар",
                categoryName = "Фантастика", rating = 8.8f
            )
        )

        // Поиск с опечаткой: "Discavery" вместо "Discovery"
        val typoChannelHits = UniversalSmartSearchEngine.searchAll(
            rawQuery = "Discavery",
            filterCategory = SearchResultCategory.ALL,
            channels = channels,
            programs = programs,
            vodItems = vod
        )
        assertEquals(1, typoChannelHits.size)
        assertEquals("ch_1", typoChannelHits.first().id)

        // Поиск с опечаткой по VOD: "Интерстелар" вместо "Интерстеллар"
        val typoVodHits = UniversalSmartSearchEngine.searchAll(
            rawQuery = "Интерстелар",
            filterCategory = SearchResultCategory.MOVIES,
            channels = channels,
            programs = programs,
            vodItems = vod
        )
        assertEquals(1, typoVodHits.size)
        assertEquals("vod_99", typoVodHits.first().id)
    }

    @Test
    fun `SettingsSecuritySessionGuard automatically locks settings after inactivity timeout`() {
        val guard = SettingsSecuritySessionGuard(autoLockTimeoutMs = 300_000L) // 5 минут
        val t0 = 1_000_000L

        assertFalse(guard.isUnlocked(t0))
        assertFalse(guard.tryUnlock("1111", "4321", t0))
        assertTrue(guard.tryUnlock("4321", "4321", t0))

        // Через 2 минуты настройки всё ещё разблокированы
        assertTrue(guard.isUnlocked(t0 + 120_000L))

        // Через 6 минут срабатывает автоматическая блокировка (п. 16 ТЗ)
        assertFalse(guard.isUnlocked(t0 + 360_000L))
    }
}
