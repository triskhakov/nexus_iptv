package com.nexus.iptv.feature.epg

import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.feature.epg.matcher.SmartEpgMatcher
import com.nexus.iptv.feature.epg.parser.XmltvChannelDescriptor
import com.nexus.iptv.feature.epg.parser.XmltvDateTimeParser
import com.nexus.iptv.feature.epg.parser.XmltvStreamingParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class Stage7XmltvAndMatcherTest {

    @Test
    fun `XmltvDateTimeParser parses timezone offsets and user timeshift accurately`() {
        val utcWithPlus5 = XmltvDateTimeParser.parseXmltvTimestamp("20261118190000 +0500", userTimeshiftMinutes = 0)
        val utcDirect = XmltvDateTimeParser.parseXmltvTimestamp("20261118140000 +0000", userTimeshiftMinutes = 0)

        assertNotNull(utcWithPlus5)
        assertEquals(utcDirect, utcWithPlus5)

        val shiftedPlus60 = XmltvDateTimeParser.parseXmltvTimestamp("20261118140000 +0000", userTimeshiftMinutes = 60)
        assertEquals(utcDirect!! + 3_600_000L, shiftedPlus60)
    }

    @Test
    fun `XmltvStreamingParser recovers from malformed and incomplete XMLTV blocks without crashing`() = runTest {
        val corruptedXmltv = listOf(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<tv generator-info-name=\"NexusTest\">",
            "  <channel id=\"disc.sci\">",
            "    <display-name>Discovery Science</display-name>",
            "    <icon src=\"http://logos.tv/disc.png\" />",
            "  </channel>",
            "  <programme start=\"BROKEN_DATE\" stop=\"20261118200000 +0000\" channel=\"disc.sci\">",
            "    <title>Битая передача</title>",
            "  </programme>",
            "  <programme start=\"20261118180000 +0000\" stop=\"20261118190000 +0000\" channel=\"disc.sci\">",
            "    <title>Как устроена Вселенная &amp; Космос</title>",
            "    <desc>Документальный цикл о квантовой физике.</desc>",
            "  </programme>",
            "  <programme start=\"20261118190000 +0000\" stop=\"20261118200000 +0000\" channel=\"disc.sci\">",
            "    <title>Незакрытая передача"
        ).joinToString("\n")

        val parser = XmltvStreamingParser(batchSize = 10)
        val channels = mutableListOf<XmltvChannelDescriptor>()
        val programs = mutableListOf<EpgProgramEntity>()

        val stats = parser.parseStream(
            sourceId = 1L,
            reader = BufferedReader(StringReader(corruptedXmltv)),
            onChannelDiscovered = { channels.add(it) },
            onProgramBatchReady = { programs.addAll(it) }
        )

        assertEquals(1, stats.parsedChannelsCount)
        assertEquals(1, stats.parsedProgramsCount)
        assertTrue(stats.skippedMalformedBlocksCount >= 2)
        assertEquals("Как устроена Вселенная & Космос", programs.first().title)
    }

    @Test
    fun `SmartEpgMatcher matches by manual ID, exact tvg-id, normalized name and fuzzy Dice similarity`() {
        val xmltvDescriptors = listOf(
            XmltvChannelDescriptor("id_natgeo", listOf("National Geographic"), null),
            XmltvChannelDescriptor("id_euro1", listOf("Eurosport 1"), null),
            XmltvChannelDescriptor("id_viasat_hist", listOf("Viasat History"), null)
        )

        val channels = listOf(
            ChannelEntity(
                id = 101L, sourceId = 1L, channelNumber = 1,
                name = "National Geographic FHD [50fps]", normalizedName = "national geographic fhd",
                categoryName = "Познавательные", streamUrl = "http://s/1", unifiedGroupKey = "national_geographic"
            ),
            ChannelEntity(
                id = 102L, sourceId = 1L, channelNumber = 2,
                name = "Спорт Канал 1", normalizedName = "спорт канал 1",
                manualEpgChannelId = "id_euro1",
                categoryName = "Спорт", streamUrl = "http://s/2", unifiedGroupKey = "sport_1"
            ),
            ChannelEntity(
                id = 103L, sourceId = 1L, channelNumber = 3,
                name = "Viasat Histori HD", normalizedName = "viasat histori hd",
                categoryName = "Познавательные", streamUrl = "http://s/3", unifiedGroupKey = "viasat_histori"
            )
        )

        val matched = SmartEpgMatcher.matchChannelsToXmltv(channels, xmltvDescriptors)

        assertEquals("id_natgeo", matched[101L])
        assertEquals("id_euro1", matched[102L])
        assertEquals("id_viasat_hist", matched[103L])
    }
}
