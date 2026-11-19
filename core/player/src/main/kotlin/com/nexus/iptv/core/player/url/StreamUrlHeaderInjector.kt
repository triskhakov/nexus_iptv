package com.nexus.iptv.core.player.url

/**
 * Разбирает URL потока с встроенными M3U/Kodi/VLC-модификаторами заголовков вида:
 * http://cdn.example.com/live/101.m3u8|User-Agent=CustomAgent/2.0&Referer=http://portal.tv/
 *
 * Возвращает чистый URL для MediaItem и карту заголовков HTTP для OkHttpDataSource.
 */
data class ParsedStreamRequest(
    val cleanUrl: String,
    val headers: Map<String, String>,
    val detectedMimeType: String?
)

object StreamUrlHeaderInjector {

    fun parse(rawStreamUrl: String, explicitHeaders: Map<String, String> = emptyMap()): ParsedStreamRequest {
        val trimmed = rawStreamUrl.trim()
        val pipeIndex = trimmed.indexOf('|')
        val cleanUrl = if (pipeIndex != -1) trimmed.substring(0, pipeIndex).trim() else trimmed

        val mergedHeaders = LinkedHashMap<String, String>(explicitHeaders)
        if (pipeIndex != -1 && pipeIndex + 1 < trimmed.length) {
            val rawHeaderBlock = trimmed.substring(pipeIndex + 1)
            rawHeaderBlock.split("&").forEach { pair ->
                val eqIdx = pair.indexOf('=')
                if (eqIdx > 0) {
                    val key = pair.substring(0, eqIdx).trim()
                    val value = pair.substring(eqIdx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        mergedHeaders[key] = value
                    }
                }
            }
        }

        return ParsedStreamRequest(
            cleanUrl = cleanUrl,
            headers = mergedHeaders,
            detectedMimeType = inferMimeType(cleanUrl)
        )
    }

    /**
     * Определяет MIME-тип контейнера для потоков HLS, MPEG-TS, DASH, MP4, MKV и RTSP (п. 8 ТЗ).
     */
    fun inferMimeType(url: String): String? {
        val pathOnly = url.substringBefore('?').lowercase()
        return when {
            pathOnly.endsWith(".m3u8") || pathOnly.contains("/hls/") -> "application/x-mpegURL"
            pathOnly.endsWith(".mpd") || pathOnly.contains("/dash/") -> "application/dash+xml"
            pathOnly.endsWith(".ts") || pathOnly.endsWith(".mpegts") -> "video/mp2t"
            pathOnly.endsWith(".mkv") -> "video/x-matroska"
            pathOnly.endsWith(".mp4") -> "video/mp4"
            pathOnly.startsWith("rtsp://") -> "application/x-rtsp"
            else -> null
        }
    }
}
