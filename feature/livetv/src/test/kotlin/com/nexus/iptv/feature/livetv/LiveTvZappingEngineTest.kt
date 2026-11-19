package com.nexus.iptv.feature.livetv

import com.nexus.iptv.feature.livetv.domain.ChannelSortMode
import com.nexus.iptv.feature.livetv.domain.LiveChannelUiModel
import com.nexus.iptv.feature.livetv.domain.LiveTvZappingEngine
import com.nexus.iptv.feature.livetv.domain.NumericKeypadChannelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveTvZappingEngineTest {

    private fun sampleChannels(): List<LiveChannelUiModel> = listOf(
        LiveChannelUiModel(
            id = 10L, sourceId = 1L, number = 1, name = "National Geographic HD",
            categoryName = "Познавательные", logoUrl = null, streamUrl = "http://s/1.m3u8",
            epgChannelId = "natgeo", currentProgramTitle = "Дикая природа", currentProgramDescription = null,
            nextProgramTitle = "Космос", currentProgramProgress = 0.5f, isFavorite = false, isLocked = false,
            healthScore = 88, hasCatchup = true, customSortOrder = 2, lastWatchedEpochMs = 1000L
        ),
        LiveChannelUiModel(
            id = 20L, sourceId = 1L, number = 2, name = "Discovery Science 4K",
            categoryName = "Познавательные", logoUrl = null, streamUrl = "http://s/2.m3u8",
            epgChannelId = "disc", currentProgramTitle = "Квантовый мир", currentProgramDescription = null,
            nextProgramTitle = "Будущее", currentProgramProgress = 0.3f, isFavorite = true, isLocked = false,
            healthScore = 99, hasCatchup = true, customSortOrder = 1, lastWatchedEpochMs = 5000L
        ),
        LiveChannelUiModel(
            id = 30L, sourceId = 1L, number = 3, name = "Eurosport 1 HD",
            categoryName = "Спорт", logoUrl = null, streamUrl = "http://s/3.m3u8",
            epgChannelId = "euro1", currentProgramTitle = "Настольный теннис", currentProgramDescription = null,
            nextProgramTitle = "Снукер", currentProgramProgress = 0.8f, isFavorite = false, isLocked = false,
            healthScore = 94, hasCatchup = false, customSortOrder = 3, lastWatchedEpochMs = 3000L
        )
    )

    @Test
    fun `resolveAdjacentChannel wraps around seamlessly on CH+ and CH- in fullscreen`() {
        val list = sampleChannels()

        // CH+ с последнего канала (30L) переключает на первый (10L)
        val wrappedForward = LiveTvZappingEngine.resolveAdjacentChannel(list, currentChannelId = 30L, step = +1)
        assertNotNull(wrappedForward)
        assertEquals(10L, wrappedForward!!.id)

        // CH- с первого канала (10L) переключает на последний (30L)
        val wrappedBackward = LiveTvZappingEngine.resolveAdjacentChannel(list, currentChannelId = 10L, step = -1)
        assertNotNull(wrappedBackward)
        assertEquals(30L, wrappedBackward!!.id)
    }

    @Test
    fun `buildQuickSwitchQueue excludes active channel and preserves recent watch order`() {
        val map = sampleChannels().associateBy { it.id }
        val queue = LiveTvZappingEngine.buildQuickSwitchQueue(
            recentHistoryIds = listOf(20L, 30L, 10L, 30L),
            allChannelsById = map,
            activeChannelId = 20L
        )

        assertEquals(2, queue.size)
        assertEquals(30L, queue[0].id)
        assertEquals(10L, queue[1].id)
    }

    @Test
    fun `filterAndSortChannels filters by EPG program title and sorts by HealthScore`() {
        val list = sampleChannels()
        val byEpgQuery = LiveTvZappingEngine.filterAndSortChannels(
            channels = list,
            selectedCategory = "Все каналы",
            searchQuery = "теннис",
            onlyFavorites = false,
            sortMode = ChannelSortMode.HEALTH_SCORE
        )
        assertEquals(1, byEpgQuery.size)
        assertEquals(30L, byEpgQuery.first().id)

        val sortedByHealth = LiveTvZappingEngine.filterAndSortChannels(
            channels = list,
            selectedCategory = null,
            searchQuery = "",
            onlyFavorites = false,
            sortMode = ChannelSortMode.HEALTH_SCORE
        )
        assertEquals(listOf(20L, 30L, 10L), sortedByHealth.map { it.id })
    }

    @Test
    fun `NumericKeypadChannelBuffer accumulates up to 4 remote digits`() {
        val keypad = NumericKeypadChannelBuffer(maxDigits = 4)
        assertEquals(1, keypad.appendDigit(1))
        assertEquals(10, keypad.appendDigit(0))
        assertEquals(105, keypad.appendDigit(5))
    }
}
