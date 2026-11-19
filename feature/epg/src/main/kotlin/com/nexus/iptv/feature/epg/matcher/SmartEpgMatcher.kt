package com.nexus.iptv.feature.epg.matcher

import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.feature.epg.parser.XmltvChannelDescriptor

object SmartEpgMatcher {

    private val QUALITY_NOISE_REGEX = Regex("(?i)\\b(uhd|4k|fhd|hd|sd|hevc|h265|50fps|60fps|orig|backup|резерв)\\b|\\[.*?\\]|\\(.*?\\)")
    private val NON_ALPHANUM_REGEX = Regex("[^a-zа-яё0-9]+")

    fun normalizeForMatch(raw: String): String {
        val stripped = QUALITY_NOISE_REGEX.replace(raw.lowercase(), " ")
        return NON_ALPHANUM_REGEX.replace(stripped, "").trim()
    }

    fun matchChannelsToXmltv(
        channels: List<ChannelEntity>,
        xmltvDescriptors: List<XmltvChannelDescriptor>
    ): Map<Long, String> {
        if (channels.isEmpty() || xmltvDescriptors.isEmpty()) return emptyMap()

        val exactIdIndex = HashMap<String, String>(xmltvDescriptors.size)
        val normalizedNameIndex = HashMap<String, String>(xmltvDescriptors.size * 2)

        for (desc in xmltvDescriptors) {
            exactIdIndex[desc.epgChannelId.lowercase()] = desc.epgChannelId
            val normId = normalizeForMatch(desc.epgChannelId)
            if (normId.isNotEmpty()) {
                normalizedNameIndex.putIfAbsent(normId, desc.epgChannelId)
            }
            for (displayName in desc.displayNames) {
                val normName = normalizeForMatch(displayName)
                if (normName.isNotEmpty()) {
                    normalizedNameIndex.putIfAbsent(normName, desc.epgChannelId)
                }
            }
        }

        val result = HashMap<Long, String>()

        for (channel in channels) {
            if (!channel.manualEpgChannelId.isNullOrBlank()) {
                result[channel.id] = channel.manualEpgChannelId!!
                continue
            }

            val rawTvgId = channel.epgChannelId?.trim()?.lowercase()
            if (!rawTvgId.isNullOrEmpty() && exactIdIndex.containsKey(rawTvgId)) {
                result[channel.id] = exactIdIndex.getValue(rawTvgId)
                continue
            }

            val normChannelName = normalizeForMatch(channel.name)
            if (normChannelName.isNotEmpty() && normalizedNameIndex.containsKey(normChannelName)) {
                result[channel.id] = normalizedNameIndex.getValue(normChannelName)
                continue
            }

            if (normChannelName.length >= 4) {
                var bestScore = 0.0
                var bestEpgId: String? = null
                for ((candidateNorm, epgId) in normalizedNameIndex) {
                    if (candidateNorm.length < 4) continue
                    val score = diceBigramSimilarity(normChannelName, candidateNorm)
                    if (score >= 0.82 && score > bestScore) {
                        bestScore = score
                        bestEpgId = epgId
                    }
                }
                if (bestEpgId != null) {
                    result[channel.id] = bestEpgId
                }
            }
        }

        return result
    }

    fun diceBigramSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0

        val bigramsA = HashMap<String, Int>(a.length)
        for (i in 0 until a.length - 1) {
            val bg = a.substring(i, i + 2)
            bigramsA[bg] = (bigramsA[bg] ?: 0) + 1
        }

        var intersection = 0
        for (i in 0 until b.length - 1) {
            val bg = b.substring(i, i + 2)
            val count = bigramsA[bg] ?: 0
            if (count > 0) {
                intersection++
                bigramsA[bg] = count - 1
            }
        }

        return (2.0 * intersection) / ((a.length - 1) + (b.length - 1))
    }
}
