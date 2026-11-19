package com.nexus.iptv.core.streamengine

import com.nexus.iptv.core.streamengine.policy.SmartStreamConfig
import com.nexus.iptv.core.streamengine.policy.SmartStreamMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Stage11SmartStreamEngineTest {

    @Test
    fun `computeExponentialBackoffMs grows exponentially and respects maxRetryDelayMs ceiling`() {
        val config = SmartStreamConfig(
            initialRetryDelayMs = 1_000L,
            maxRetryDelayMs = 10_000L,
            backoffMultiplier = 2.0
        )
        val fixedRng = Random(42)

        val d1 = SmartStreamMath.computeExponentialBackoffMs(1, config, fixedRng)
        val d2 = SmartStreamMath.computeExponentialBackoffMs(2, config, fixedRng)
        val d3 = SmartStreamMath.computeExponentialBackoffMs(3, config, fixedRng)
        val d5 = SmartStreamMath.computeExponentialBackoffMs(5, config, fixedRng)

        // Попытка 1: ~1000..1250 мс
        assertTrue(d1 in 1_000L..1_250L)
        // Попытка 2: ~2000..2250 мс
        assertTrue(d2 in 2_000L..2_250L)
        // Попытка 3: ~4000..4250 мс
        assertTrue(d3 in 4_000L..4_250L)
        // Попытка 5: упирается в потолок maxRetryDelayMs (10 000 + джиттер до 250 мс)
        assertTrue(d5 in 10_000L..10_250L)
    }

    @Test
    fun `calculateChannelHealthScore accurately scores fast stable streams vs degraded streams`() {
        // 1. Идеальный канал: старт за 650 мс, 0 ребуферов, 0 ошибок, 0 дропов -> 100 баллов
        val idealScore = SmartStreamMath.calculateChannelHealthScore(
            startupDurationMs = 650L,
            rebufferCount = 0,
            failureCount = 0,
            droppedFramesCount = 0
        )
        assertEquals(100, idealScore)

        // 2. Проблемный канал: старт 4500 мс (-14), 3 ребуфера (-18), 2 ошибки (-30), 50 дропов (-5) -> 33 балла
        val degradedScore = SmartStreamMath.calculateChannelHealthScore(
            startupDurationMs = 4_500L,
            rebufferCount = 3,
            failureCount = 2,
            droppedFramesCount = 50
        )
        assertEquals(33, degradedScore)
    }

    @Test
    fun `diagnoseNetworkIssue provides human-readable root cause explanations`() {
        val timeoutHint = SmartStreamMath.diagnoseNetworkIssue("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT", 0L, 0L)
        assertTrue(timeoutHint.contains("Таймаут соединения"))

        val authHint = SmartStreamMath.diagnoseNetworkIssue("ERROR_CODE_IO_BAD_HTTP_STATUS_403", 0L, 0L)
        assertTrue(authHint.contains("HTTP 401/403"))

        val speedHint = SmartStreamMath.diagnoseNetworkIssue("STALL_WATCHDOG", bufferedMs = 100L, bitrateKbps = 12_000L)
        assertTrue(speedHint.contains("Недостаточная скорость загрузки"))
    }
}
