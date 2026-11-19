package com.nexus.iptv.core.player

import com.nexus.iptv.core.player.url.StreamUrlHeaderInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamUrlHeaderInjectorTest {

    @Test
    fun `StreamUrlHeaderInjector extracts clean URL, inline pipe headers and MIME types accurately`() {
        val rawUrl = "http://cdn.provider.tv/live/101/index.m3u8?token=abc|User-Agent=CustomExo/2.1&Referer=http://portal.tv/"
        val parsed = StreamUrlHeaderInjector.parse(
            rawStreamUrl = rawUrl,
            explicitHeaders = mapOf("X-Custom-Auth" to "nexus")
        )

        assertEquals("http://cdn.provider.tv/live/101/index.m3u8?token=abc", parsed.cleanUrl)
        assertEquals("CustomExo/2.1", parsed.headers["User-Agent"])
        assertEquals("http://portal.tv/", parsed.headers["Referer"])
        assertEquals("nexus", parsed.headers["X-Custom-Auth"])
        assertEquals("application/x-mpegURL", parsed.detectedMimeType)
    }

    @Test
    fun `inferMimeType recognizes HLS, DASH, MPEG-TS, MKV, MP4 and RTSP formats`() {
        assertEquals("application/x-mpegURL", StreamUrlHeaderInjector.inferMimeType("https://s1.tv/stream.m3u8"))
        assertEquals("application/dash+xml", StreamUrlHeaderInjector.inferMimeType("https://s1.tv/manifest.mpd"))
        assertEquals("video/mp2t", StreamUrlHeaderInjector.inferMimeType("http://portal.tv:8080/live/u/p/405.ts"))
        assertEquals("video/x-matroska", StreamUrlHeaderInjector.inferMimeType("http://vod.tv/movie_4k.mkv"))
        assertEquals("video/mp4", StreamUrlHeaderInjector.inferMimeType("http://vod.tv/episode_01.mp4"))
        assertEquals("application/x-rtsp", StreamUrlHeaderInjector.inferMimeType("rtsp://192.168.1.50:554/live.sdp"))
        assertNull(StreamUrlHeaderInjector.inferMimeType("http://portal.tv:8080/stream/1024"))
    }
}
