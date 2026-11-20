package com.nexus.iptv.feature.sources

import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.feature.sources.parser.M3uHeaderMetadata
import com.nexus.iptv.feature.sources.parser.M3uStreamingParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class M3uStreamingParserTest {

    @Test
    fun `M3uStreamingParser extracts header EPG, catchup attributes, EXTVLCOPT user-agent and unified group key`() = runTest {
        val sampleM3u = listOf(
            "#EXTM3U url-tvg=\"http://epg.provider.com/xmltv.xml.gz, http://backup.epg/tv.xml\" catchup=\"default\" catchup-days=\"7\" tvg-shift=\"+2\"",
            "#EXTINF:-1 tvg-id=\"disc.sci\" tvg-name=\"Discovery Science\" tvg-logo=\"http://logos.com/disc.png\" group-title=\"Познавательные\" catchup=\"flussonic\" catchup-days=\"5\" catchup-source=\"http://arch.com/{utc}.m3u8\" tvg-chno=\"101\",Discovery Science HD",
            "#EXTVLCOPT:http-user-agent=CustomPlayer/4.0",
            "http://stream.provider.com/live/101/index.m3u8",
            "#EXTINF:-1 tvg-id=\"disc.sci\" group-title=\"Познавательные\",Discovery Science FHD [Backup]",
            "#EXTGRP:Документальные",
            "http://backup.provider.com/live/101_fhd/index.m3u8"
        ).joinToString("\n")

        val parser = M3uStreamingParser(batchSize = 1)
        var capturedHeader: M3uHeaderMetadata? = null
        val parsedChannels = mutableListOf<ChannelEntity>()

        val total = parser.parseStream(
            sourceId = 7L,
            reader = BufferedReader(StringReader(sampleM3u)),
            onHeaderParsed = { capturedHeader = it },
            onBatchReady = { batch -> parsedChannels.addAll(batch) }
        )

        assertEquals(2, total)
        assertNotNull(capturedHeader)
        assertEquals(2, capturedHeader!!.discoveredEpgUrls.size)
        assertEquals("http://epg.provider.com/xmltv.xml.gz", capturedHeader!!.discoveredEpgUrls.first())
        assertEquals(7, capturedHeader!!.globalCatchupDays)
        assertEquals(120, capturedHeader!!.globalTimeshiftMinutes)

        val ch1 = parsedChannels[0]
        assertEquals(101, ch1.channelNumber)
        assertEquals("Discovery Science HD", ch1.name)
        assertEquals("Познавательные", ch1.categoryName)
        assertEquals("FLUSSONIC", ch1.catchupType)
        assertEquals(5, ch1.catchupDays)
        assertEquals("http://arch.com/{utc}.m3u8", ch1.catchupSourceTemplate)
        assertTrue(ch1.streamUrl.endsWith("|User-Agent=CustomPlayer/4.0"))

        val ch2 = parsedChannels[1]
        assertEquals("discovery_science", ch1.unifiedGroupKey)
        assertEquals("discovery_science", ch2.unifiedGroupKey)
        assertEquals(7, ch2.catchupDays)
    }

    @Test
    fun `M3uStreamingParser streams 10000 channels in chunks of 500 without memory spikes`() = runTest {
        val sb = StringBuilder("#EXTM3U\n")
        for (i in 1..10_000) {
            sb.append("#EXTINF:-1 tvg-id=\"ch$i\" group-title=\"Group ${i % 20}\",Channel Number $i HD\n")
            sb.append("http://cdn.provider.net/live/stream_$i.m3u8\n")
        }

        val parser = M3uStreamingParser(batchSize = 500)
        var batchCallbacksCount = 0
        var totalInserted = 0

        val total = parser.parseStream(
            sourceId = 1L,
            reader = BufferedReader(StringReader(sb.toString())),
            onBatchReady = { batch ->
                batchCallbacksCount++
                totalInserted += batch.size
                assertTrue(batch.size <= 500)
            }
        )

        assertEquals(10_000, total)
        assertEquals(10_000, totalInserted)
        assertEquals(20, batchCallbacksCount)
    }
}
