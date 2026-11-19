package com.nexus.iptv.feature.epg.parser

import com.nexus.iptv.core.database.entity.EpgProgramEntity
import java.io.BufferedReader

data class XmltvChannelDescriptor(
    val epgChannelId: String,
    val displayNames: List<String>,
    val iconUrl: String?
)

data class XmltvParseStats(
    val parsedChannelsCount: Int,
    val parsedProgramsCount: Int,
    val skippedMalformedBlocksCount: Int
)

class XmltvStreamingParser(
    private val batchSize: Int = 500
) {
    companion object {
        private val CHANNEL_ID_ATTR = Regex("id\\s*=\\s*\"([^\"]+)\"")
        private val DISPLAY_NAME_TAG = Regex("<display-name[^>]*>(.*?)</display-name>", RegexOption.IGNORE_CASE)
        private val ICON_SRC_ATTR = Regex("<icon[^>]+src\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

        private val PROG_CHANNEL_ATTR = Regex("channel\\s*=\\s*\"([^\"]+)\"")
        private val PROG_START_ATTR = Regex("start\\s*=\\s*\"([^\"]+)\"")
        private val PROG_STOP_ATTR = Regex("stop\\s*=\\s*\"([^\"]+)\"")
        private val TITLE_TAG = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val DESC_TAG = Regex("<desc[^>]*>(.*?)</desc>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }

    suspend fun parseStream(
        sourceId: Long,
        reader: BufferedReader,
        timeshiftMinutes: Int = 0,
        minEndEpochMsFilter: Long = 0L,
        onChannelDiscovered: suspend (XmltvChannelDescriptor) -> Unit = {},
        onProgramBatchReady: suspend (List<EpgProgramEntity>) -> Unit
    ): XmltvParseStats {
        val programBatch = ArrayList<EpgProgramEntity>(batchSize)
        var channelsCount = 0
        var programsCount = 0
        var malformedCount = 0

        val blockBuffer = StringBuilder(2048)
        var insideChannel = false
        var insideProgramme = false

        var rawLine: String? = reader.readLine()
        while (rawLine != null) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                rawLine = reader.readLine()
                continue
            }

            if (!insideProgramme && (insideChannel || line.contains("<channel", ignoreCase = true))) {
                insideChannel = true
                blockBuffer.append(line).append(' ')
                if (line.contains("</channel>", ignoreCase = true) ||
                    (line.contains("<channel", ignoreCase = true) && line.endsWith("/>"))
                ) {
                    val block = blockBuffer.toString()
                    blockBuffer.setLength(0)
                    insideChannel = false
                    val descriptor = parseChannelBlockSafely(block)
                    if (descriptor != null) {
                        channelsCount++
                        onChannelDiscovered(descriptor)
                    } else {
                        malformedCount++
                    }
                }
                rawLine = reader.readLine()
                continue
            }

            if (insideProgramme || line.contains("<programme", ignoreCase = true)) {
                if (insideProgramme && line.contains("<programme", ignoreCase = true)) {
                    malformedCount++
                    blockBuffer.setLength(0)
                }
                insideProgramme = true
                blockBuffer.append(line).append('\n')

                if (line.contains("</programme>", ignoreCase = true)) {
                    val block = blockBuffer.toString()
                    blockBuffer.setLength(0)
                    insideProgramme = false

                    val entity = parseProgrammeBlockSafely(
                        sourceId = sourceId,
                        block = block,
                        timeshiftMinutes = timeshiftMinutes
                    )
                    if (entity != null) {
                        if (entity.endEpochMs >= minEndEpochMsFilter) {
                            programBatch.add(entity)
                            programsCount++
                            if (programBatch.size >= batchSize) {
                                onProgramBatchReady(programBatch.toList())
                                programBatch.clear()
                            }
                        }
                    } else {
                        malformedCount++
                    }
                }
            }

            rawLine = reader.readLine()
        }

        if (insideChannel || insideProgramme) {
            malformedCount++
        }

        if (programBatch.isNotEmpty()) {
            onProgramBatchReady(programBatch.toList())
            programBatch.clear()
        }

        return XmltvParseStats(
            parsedChannelsCount = channelsCount,
            parsedProgramsCount = programsCount,
            skippedMalformedBlocksCount = malformedCount
        )
    }

    private fun parseChannelBlockSafely(block: String): XmltvChannelDescriptor? {
        return try {
            val headerPart = block.substringBefore('>')
            val id = CHANNEL_ID_ATTR.find(headerPart)?.groupValues?.get(1)?.trim()
            if (id.isNullOrEmpty()) return null

            val names = DISPLAY_NAME_TAG.findAll(block)
                .map { unescapeXmlEntities(it.groupValues[1].trim()) }
                .filter { it.isNotEmpty() }
                .toList()

            val iconUrl = ICON_SRC_ATTR.find(block)?.groupValues?.get(1)?.trim()
            XmltvChannelDescriptor(
                epgChannelId = id,
                displayNames = names,
                iconUrl = iconUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseProgrammeBlockSafely(
        sourceId: Long,
        block: String,
        timeshiftMinutes: Int
    ): EpgProgramEntity? {
        return try {
            val headerPart = block.substringBefore('>')
            val channelId = PROG_CHANNEL_ATTR.find(headerPart)?.groupValues?.get(1)?.trim()
            val startRaw = PROG_START_ATTR.find(headerPart)?.groupValues?.get(1)?.trim()
            val stopRaw = PROG_STOP_ATTR.find(headerPart)?.groupValues?.get(1)?.trim()

            if (channelId.isNullOrEmpty() || startRaw.isNullOrEmpty() || stopRaw.isNullOrEmpty()) {
                return null
            }

            val startEpochMs = XmltvDateTimeParser.parseXmltvTimestamp(startRaw, timeshiftMinutes) ?: return null
            val endEpochMs = XmltvDateTimeParser.parseXmltvTimestamp(stopRaw, timeshiftMinutes) ?: return null
            if (endEpochMs <= startEpochMs) return null

            val rawTitle = TITLE_TAG.find(block)?.groupValues?.get(1)?.trim()
            val title = unescapeXmlEntities(rawTitle.orEmpty()).ifEmpty { return null }

            val rawDesc = DESC_TAG.find(block)?.groupValues?.get(1)?.trim()
            val description = rawDesc?.let { unescapeXmlEntities(it) }?.takeIf { it.isNotEmpty() }
            val posterUrl = ICON_SRC_ATTR.find(block)?.groupValues?.get(1)?.trim()

            EpgProgramEntity(
                sourceId = sourceId,
                epgChannelId = channelId,
                title = title,
                description = description,
                posterUrl = posterUrl,
                startEpochMs = startEpochMs,
                endEpochMs = endEpochMs
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeXmlEntities(input: String): String {
        if (!input.contains('&') && !input.contains("<![CDATA[")) return input
        return input
            .removePrefix("<![CDATA[")
            .removeSuffix("]]>")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
    }
}
