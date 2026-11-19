package com.nexus.iptv.feature.sources.parser

import com.nexus.iptv.core.common.result.NexusException
import com.nexus.iptv.core.database.entity.ChannelEntity
import java.io.BufferedReader

data class M3uHeaderMetadata(
    val discoveredEpgUrls: List<String> = emptyList(),
    val globalCatchupType: String? = null,
    val globalCatchupDays: Int = 0,
    val globalTimeshiftMinutes: Int = 0
)

class M3uStreamingParser(
    private val batchSize: Int = 500
) {
    companion object {
        private val ATTRIBUTE_REGEX = Regex("([a-zA-Z0-9_-]+)\\s*=\\s*\"([^\"]*)\"")
        private val QUALITY_SUFFIX_REGEX = Regex("(?i)\\b(uhd|4k|fhd|hd|sd|hevc|h265|50fps|60fps|backup|резерв)\\b|\\[.*?\\]|\\(.*?\\)")
        private val NON_ALPHANUM_REGEX = Regex("[^a-zа-яё0-9]+")
    }

    suspend fun parseStream(
        sourceId: Long,
        reader: BufferedReader,
        onHeaderParsed: suspend (M3uHeaderMetadata) -> Unit = {},
        onBatchReady: suspend (List<ChannelEntity>) -> Unit
    ): Int {
        val batch = ArrayList<ChannelEntity>(batchSize)
        var totalParsedCount = 0
        var lineNumber = 0
        var autoChannelNumber = 1

        var globalMetadata = M3uHeaderMetadata()
        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingTitle: String? = null
        var pendingExtGrp: String? = null
        var pendingUserAgent: String? = null

        var rawLine: String? = reader.readLine()
        while (rawLine != null) {
            lineNumber++
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) {
                rawLine = reader.readLine()
                continue
            }

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val headerAttrs = extractAttributes(line)
                    val epgRaw = headerAttrs["url-tvg"] ?: headerAttrs["x-tvg-url"] ?: headerAttrs["tvg-url"]
                    val epgUrls = epgRaw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    val catchupDays = (headerAttrs["catchup-days"] ?: headerAttrs["tvg-rec"])?.toIntOrNull() ?: 0
                    val shiftHours = headerAttrs["tvg-shift"]?.toFloatOrNull() ?: 0f

                    globalMetadata = M3uHeaderMetadata(
                        discoveredEpgUrls = epgUrls,
                        globalCatchupType = headerAttrs["catchup"] ?: headerAttrs["catchup-type"],
                        globalCatchupDays = catchupDays,
                        globalTimeshiftMinutes = (shiftHours * 60).toInt()
                    )
                    onHeaderParsed(globalMetadata)
                }

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingAttrs = extractAttributes(line)
                    pendingTitle = extractChannelTitle(line, pendingAttrs["tvg-name"])
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingExtGrp = line.substringAfter(":").trim().takeIf { it.isNotEmpty() }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(":").trim()
                    if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                        pendingUserAgent = opt.substringAfter("=").trim()
                    }
                }

                line.startsWith("#") -> {
                    // Пропускаем прочие служебные комментарии
                }

                isValidStreamUrl(line) -> {
                    val channelName = (pendingTitle ?: deriveNameFromUrl(line)).trim().ifEmpty { "Канал $autoChannelNumber" }
                    val categoryName = (pendingAttrs["group-title"] ?: pendingExtGrp ?: "Общие").trim().ifEmpty { "Общие" }
                    val explicitChNo = pendingAttrs["tvg-chno"]?.toIntOrNull() ?: pendingAttrs["ch-number"]?.toIntOrNull()
                    val channelNumber = explicitChNo ?: autoChannelNumber
                    autoChannelNumber++

                    val catchupType = pendingAttrs["catchup"]
                        ?: pendingAttrs["catchup-type"]
                        ?: globalMetadata.globalCatchupType
                    val catchupDays = pendingAttrs["catchup-days"]?.toIntOrNull()
                        ?: pendingAttrs["tvg-rec"]?.toIntOrNull()
                        ?: globalMetadata.globalCatchupDays
                    val catchupSource = pendingAttrs["catchup-source"]

                    val normalized = channelName.lowercase()
                    val unifiedKey = buildUnifiedGroupKey(channelName)

                    val finalStreamUrl = if (!pendingUserAgent.isNullOrBlank() && !line.contains("|User-Agent=")) {
                        "$line|User-Agent=$pendingUserAgent"
                    } else {
                        line
                    }

                    batch.add(
                        ChannelEntity(
                            sourceId = sourceId,
                            channelNumber = channelNumber,
                            name = channelName,
                            normalizedName = normalized,
                            categoryName = categoryName,
                            logoUrl = pendingAttrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                            streamUrl = finalStreamUrl,
                            epgChannelId = pendingAttrs["tvg-id"]?.takeIf { it.isNotBlank() },
                            catchupType = catchupType?.uppercase(),
                            catchupDays = catchupDays,
                            catchupSourceTemplate = catchupSource,
                            unifiedGroupKey = unifiedKey
                        )
                    )
                    totalParsedCount++

                    pendingAttrs = emptyMap()
                    pendingTitle = null
                    pendingExtGrp = null
                    pendingUserAgent = null

                    if (batch.size >= batchSize) {
                        onBatchReady(batch.toList())
                        batch.clear()
                    }
                }
            }

            rawLine = reader.readLine()
        }

        if (batch.isNotEmpty()) {
            onBatchReady(batch.toList())
            batch.clear()
        }

        if (totalParsedCount == 0 && lineNumber > 0) {
            throw NexusException.PlaylistMalformedException(
                line = lineNumber,
                details = "Плейлист не содержит ни одного поддерживаемого видеопотока"
            )
        }

        return totalParsedCount
    }

    private fun extractAttributes(line: String): Map<String, String> {
        val map = HashMap<String, String>(8)
        ATTRIBUTE_REGEX.findAll(line).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()
            if (value.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun extractChannelTitle(extInfLine: String, fallbackTvgName: String?): String {
        var inQuotes = false
        var lastCommaIndex = -1
        for (i in extInfLine.indices) {
            val ch = extInfLine[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == ',' && !inQuotes) lastCommaIndex = i
        }
        val titleAfterComma = if (lastCommaIndex != -1 && lastCommaIndex + 1 < extInfLine.length) {
            extInfLine.substring(lastCommaIndex + 1).trim()
        } else ""

        return titleAfterComma.ifEmpty { fallbackTvgName.orEmpty() }
    }

    private fun isValidStreamUrl(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("udp://") ||
            lower.startsWith("rtp://") ||
            lower.startsWith("mms://")
    }

    private fun deriveNameFromUrl(url: String): String {
        return url.substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('.')
            .replace('_', ' ')
            .replace('-', ' ')
    }

    fun buildUnifiedGroupKey(rawChannelName: String): String {
        val stripped = QUALITY_SUFFIX_REGEX.replace(rawChannelName.lowercase(), " ")
        return NON_ALPHANUM_REGEX.replace(stripped.trim(), "_").trim('_').ifEmpty {
            rawChannelName.lowercase().trim()
        }
    }
}
