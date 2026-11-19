package com.nexus.iptv.feature.multiview

import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.feature.multiview.domain.MultiviewLayoutEngine
import com.nexus.iptv.feature.multiview.domain.MultiviewLayoutMode
import com.nexus.iptv.feature.multiview.hardware.HardwareMultiviewInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage10MultiviewTest {

    @Test
    fun `HardwareMultiviewInspector caps windows on 2GB RAM TV Boxes and unlocks 4 windows on flagship hardware`() {
        // 1. Бюджетный Android TV Box с 2 ГБ RAM и 1 аппаратным декодером
        val budgetBox = HardwareMultiviewInspector.evaluateCapabilityFromMetrics(
            totalRamMb = 2048L,
            availableRamMb = 620L,
            isLowRamDevice = true,
            maxHardwareAvcInstances = 1,
            maxHardwareHevcInstances = 1
        )
        assertEquals(1, budgetBox.maxRecommendedWindows)

        // 2. Устройство среднего класса (3 ГБ RAM, 2 аппаратных декодера) -> до 2 окон
        val midRangeBox = HardwareMultiviewInspector.evaluateCapabilityFromMetrics(
            totalRamMb = 3072L,
            availableRamMb = 1100L,
            isLowRamDevice = false,
            maxHardwareAvcInstances = 2,
            maxHardwareHevcInstances = 2
        )
        assertEquals(2, midRangeBox.maxRecommendedWindows)

        // 3. Мощная приставка / смартфон (6 ГБ RAM, 6 аппаратных сессий) -> 4 окна
        val flagshipDevice = HardwareMultiviewInspector.evaluateCapabilityFromMetrics(
            totalRamMb = 6144L,
            availableRamMb = 2800L,
            isLowRamDevice = false,
            maxHardwareAvcInstances = 6,
            maxHardwareHevcInstances = 4
        )
        assertEquals(4, flagshipDevice.maxRecommendedWindows)
    }

    @Test
    fun `MultiviewLayoutEngine enforces exclusive Solo Audio Focus and recalculates primary window weights`() {
        val ch1 = ChannelEntity(
            id = 1L, sourceId = 1L, channelNumber = 1, name = "Eurosport 1",
            normalizedName = "eurosport 1", categoryName = "Спорт", streamUrl = "http://s/1", unifiedGroupKey = "es1"
        )
        val ch2 = ChannelEntity(
            id = 2L, sourceId = 1L, channelNumber = 2, name = "Eurosport 2",
            normalizedName = "eurosport 2", categoryName = "Спорт", streamUrl = "http://s/2", unifiedGroupKey = "es2"
        )

        val slots = MultiviewLayoutEngine.computeSlotStates(
            layoutMode = MultiviewLayoutMode.DUAL_FOCUS,
            assignedChannels = mapOf(0 to ch1, 1 to ch2),
            primarySlotIndex = 1,    // Второе окно выбрано главным (70%)
            audioFocusSlotIndex = 1  // Звук переключён на второе окно
        )

        assertEquals(2, slots.size)
        // Окно 0: 30% размера, не главное, звук выключен (Mute)
        assertEquals(0.30f, slots[0].weightFraction, 0.001f)
        assertFalse(slots[0].isPrimaryWindow)
        assertFalse(slots[0].isAudioActive)

        // Окно 1: 70% размера, главное, звук активен
        assertEquals(0.70f, slots[1].weightFraction, 0.001f)
        assertTrue(slots[1].isPrimaryWindow)
        assertTrue(slots[1].isAudioActive)

        // Расчёт трафика: 1 главное окно (8.0 Mbps) + 1 вспомогательное окно 720p (3.5 Mbps) = 11.5 Mbps
        assertEquals(11.5f, MultiviewLayoutEngine.estimateRequiredBandwidthMbps(slots), 0.01f)
    }
}
