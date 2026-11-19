package com.nexus.iptv.feature.dvr

import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.DvrRecordingEntity
import com.nexus.iptv.feature.dvr.domain.CatchupAndTimeshiftEngine
import com.nexus.iptv.feature.dvr.storage.DvrRetentionPolicy
import com.nexus.iptv.feature.dvr.storage.DvrStorageQuotaManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage9CatchupAndDvrTest {

    @Test
    fun `evaluateCapabilities never fakes Catchup or DVR when source or storage lacks support`() {
        val channelWithoutCatchup = ChannelEntity(
            id = 1L, sourceId = 1L, channelNumber = 1, name = "Live Only Channel",
            normalizedName = "live only", categoryName = "Общие",
            streamUrl = "http://cdn.tv/live/1.m3u8",
            catchupType = null, catchupDays = 0,
            unifiedGroupKey = "live_only"
        )

        // Только 50 МБ свободного места -> ни Catch-up, ни Timeshift (>=150 МБ), ни DVR (>=500 МБ) не должны имитироваться
        val capsLowSpace = CatchupAndTimeshiftEngine.evaluateCapabilities(
            channel = channelWithoutCatchup,
            availableStorageBytes = 50L * 1024L * 1024L,
            isStorageWritable = true
        )

        assertFalse(capsLowSpace.isCatchupSupported)
        assertFalse(capsLowSpace.isLiveTimeshiftSupported)
        assertFalse(capsLowSpace.isDvrRecordingSupported)
    }

    @Test
    fun `buildCatchupUrl resolves Flussonic, Xtream, Shift and Default macro templates accurately`() {
        val nowMs = 1_763_500_000_000L
        val progStartMs = nowMs - 7_200_000L // 2 часа назад
        val progEndMs = progStartMs + 3_600_000L // Длительность 3600 сек (60 мин)
        val startSec = progStartMs / 1000L

        // 1. Flussonic archive URL transformation
        val flussonicChannel = ChannelEntity(
            id = 1L, sourceId = 1L, channelNumber = 1, name = "Discovery HD",
            normalizedName = "discovery hd", categoryName = "Познавательные",
            streamUrl = "http://flussonic.provider.tv/discovery/index.m3u8?token=xyz|User-Agent=Nexus/1.0",
            catchupType = "FLUSSONIC", catchupDays = 5,
            unifiedGroupKey = "discovery"
        )
        val flussonicUrl = CatchupAndTimeshiftEngine.buildCatchupUrl(flussonicChannel, progStartMs, progEndMs, nowMs)
        assertNotNull(flussonicUrl)
        assertEquals(
            "http://flussonic.provider.tv/discovery/index-${startSec}-3600.m3u8?token=xyz|User-Agent=Nexus/1.0",
            flussonicUrl
        )

        // 2. Xtream Codes timeshift transformation
        val xtreamChannel = ChannelEntity(
            id = 2L, sourceId = 1L, channelNumber = 2, name = "Eurosport 4K",
            normalizedName = "eurosport 4k", categoryName = "Спорт",
            streamUrl = "http://portal.tv:8080/live/user123/pass456/905.ts",
            catchupType = "XTREAM", catchupDays = 7,
            unifiedGroupKey = "eurosport"
        )
        val xtreamUrl = CatchupAndTimeshiftEngine.buildCatchupUrl(xtreamChannel, progStartMs, progEndMs, nowMs)
        assertNotNull(xtreamUrl)
        assertTrue(xtreamUrl!!.startsWith("http://portal.tv:8080/timeshift/user123/pass456/60/"))
        assertTrue(xtreamUrl.endsWith("/905.ts"))

        // 3. Проверка защиты глубины архива (передача 10 дней назад при catchupDays = 5 -> null)
        val expiredStartMs = nowMs - 10 * 86_400_000L
        assertNull(CatchupAndTimeshiftEngine.buildCatchupUrl(flussonicChannel, expiredStartMs, expiredStartMs + 3_600_000L, nowMs))
    }

    @Test
    fun `DvrStorageQuotaManager deletes expired and over-quota recordings while preserving pinned recordings`() {
        val nowMs = 1_763_500_000_000L
        val mb = 1024L * 1024L

        val recordings = listOf(
            // 1. Очень старая запись (20 дней назад), но ЗАКРЕПЛЕНА пользователем (isPinned = true) -> НЕ УДАЛЯТЬ
            DvrRecordingEntity(
                id = 1L, channelId = 10L, channelName = "Ch1", programTitle = "Pinned Final Match",
                streamUrl = "http://s/1", outputFilePath = "/tmp/1.ts",
                scheduledStartEpochMs = nowMs - 20 * 86_400_000L, scheduledEndEpochMs = nowMs - 20 * 86_400_000L + 3600_000L,
                recordedBytes = 600 * mb, status = "COMPLETED", isPinned = true
            ),
            // 2. Старая незакреплённая запись (16 дней назад, старше лимита 14 дней) -> УДАЛИТЬ по сроку давности
            DvrRecordingEntity(
                id = 2L, channelId = 10L, channelName = "Ch1", programTitle = "Expired News",
                streamUrl = "http://s/1", outputFilePath = "/tmp/2.ts",
                scheduledStartEpochMs = nowMs - 16 * 86_400_000L, scheduledEndEpochMs = nowMs - 16 * 86_400_000L + 3600_000L,
                recordedBytes = 500 * mb, status = "COMPLETED", isPinned = false
            ),
            // 3. Свежая запись (2 дня назад), но вызывает превышение квоты 1000 МБ -> УДАЛИТЬ по квоте FIFO
            DvrRecordingEntity(
                id = 3L, channelId = 10L, channelName = "Ch1", programTitle = "Quota Overflow Movie",
                streamUrl = "http://s/1", outputFilePath = "/tmp/3.ts",
                scheduledStartEpochMs = nowMs - 2 * 86_400_000L, scheduledEndEpochMs = nowMs - 2 * 86_400_000L + 3600_000L,
                recordedBytes = 700 * mb, status = "COMPLETED", isPinned = false
            ),
            // 4. Самая свежая запись (1 день назад), после удаления #2 и #3 общий объём (#1 + #4 = 900 МБ) укладывается в квоту 1000 МБ -> ОСТАВИТЬ
            DvrRecordingEntity(
                id = 4L, channelId = 10L, channelName = "Ch1", programTitle = "Recent Science Show",
                streamUrl = "http://s/1", outputFilePath = "/tmp/4.ts",
                scheduledStartEpochMs = nowMs - 1 * 86_400_000L, scheduledEndEpochMs = nowMs - 1 * 86_400_000L + 3600_000L,
                recordedBytes = 300 * mb, status = "COMPLETED", isPinned = false
            )
        )

        val policy = DvrRetentionPolicy(
            maxStorageQuotaBytes = 1000 * mb,
            maxRecordingAgeDays = 14,
            minFreeDeviceSpaceBytes = 500 * mb
        )

        val toDelete = DvrStorageQuotaManager.selectRecordingsForAutoCleanup(
            completedRecordingsOldestFirst = recordings,
            policy = policy,
            currentDeviceFreeBytes = 2000 * mb,
            nowEpochMs = nowMs
        )

        assertEquals(listOf(2L, 3L), toDelete.map { it.id })
    }
}
