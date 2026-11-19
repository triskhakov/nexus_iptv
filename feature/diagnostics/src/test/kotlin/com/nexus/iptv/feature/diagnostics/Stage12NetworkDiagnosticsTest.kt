package com.nexus.iptv.feature.diagnostics

import com.nexus.iptv.feature.diagnostics.domain.NetworkDiagnosticsEngine
import com.nexus.iptv.feature.diagnostics.domain.StreamQualityReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage12NetworkDiagnosticsTest {

    @Test
    fun `classifyNetworkReadiness accurately categorizes Offline, SD, FullHD and 4K UHD bandwidths`() {
        assertEquals(
            StreamQualityReadiness.OFFLINE,
            NetworkDiagnosticsEngine.classifyNetworkReadiness(isConnected = false, downloadSpeedMbps = 0f, latencyMs = 0L)
        )

        assertEquals(
            StreamQualityReadiness.SD_ONLY,
            NetworkDiagnosticsEngine.classifyNetworkReadiness(isConnected = true, downloadSpeedMbps = 3.2f, latencyMs = 60L)
        )

        assertEquals(
            StreamQualityReadiness.HD_READY,
            NetworkDiagnosticsEngine.classifyNetworkReadiness(isConnected = true, downloadSpeedMbps = 11.5f, latencyMs = 45L)
        )

        assertEquals(
            StreamQualityReadiness.UHD_4K_READY,
            NetworkDiagnosticsEngine.classifyNetworkReadiness(isConnected = true, downloadSpeedMbps = 48.0f, latencyMs = 28L)
        )
    }

    @Test
    fun `buildUserAndEngineerSummary generates both plain-language and technical reports`() {
        val (userText, techText) = NetworkDiagnosticsEngine.buildUserAndEngineerSummary(
            isConnected = true,
            transport = "Ethernet (Кабель)",
            latencyMs = 24L,
            speedMbps = 65.4f,
            readiness = StreamQualityReadiness.UHD_4K_READY
        )

        assertTrue(userText.contains("4K UHD"))
        assertTrue(userText.contains("65.4"))
        assertTrue(techText.contains("Transport=Ethernet (Кабель)"))
        assertTrue(techText.contains("RTT=24ms"))
        assertTrue(techText.contains("Verdict=UHD_4K_READY"))
    }
}
