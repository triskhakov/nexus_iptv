package com.nexus.iptv.feature.diagnostics.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.common.security.UrlRedactor
import com.nexus.iptv.core.database.entity.SourceEntity
import com.nexus.iptv.core.network.client.NexusStreamNetworkClient
import com.nexus.iptv.core.player.contract.PlaybackTelemetry
import com.nexus.iptv.core.streamengine.policy.SmartStreamStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class StreamQualityReadiness(val titleRu: String) {
    OFFLINE("Нет подключения к сети"),
    SD_ONLY("Достаточно только для SD (до 5 Мбит/с)"),
    HD_READY("Готово для HD 720p / 1080p (5–15 Мбит/с)"),
    UHD_4K_READY("Отлично: готово для 4K UHD и Multiview (> 15 Мбит/с)")
}

data class InternetDiagnosticSnapshot(
    val isInternetConnected: Boolean,
    val transportType: String,               // Wi-Fi, Ethernet, Cellular, None
    val latencyMs: Long,
    val downloadSpeedMbps: Float,
    val readinessVerdict: StreamQualityReadiness,
    val userSummary: String,
    val technicalDetails: String
)

data class SourceProbeDiagnosticResult(
    val sourceId: Long,
    val sourceName: String,
    val isPlaylistReachable: Boolean,
    val playlistHttpCode: Int,
    val playlistConnectTimeMs: Long,
    val isEpgConfigured: Boolean,
    val isEpgReachable: Boolean,
    val epgHttpCode: Int,
    val epgConnectTimeMs: Long,
    val userFriendlyMessage: String,
    val engineerLogLine: String
)

/**
 * Модуль сетевой и системной диагностики — Network Diagnostics (Этап 12, п. 17 ТЗ).
 *
 * Выполняет:
 * 1. Проверку активного сетевого интерфейса (Ethernet / Wi-Fi / Мобильная сеть) и DNS/HTTP-задержки;
 * 2. Замер реальной скорости загрузки тестового потока (Мбит/с) с классификацией готовности к SD / HD / 4K;
 * 3. Глубокую проверку выбранного IPTV-источника и связанного XMLTV EPG («Проверить источник»);
 * 4. Формирование двухуровневого отчёта: понятного обычному пользователю и детализированного для специалиста.
 */
@Singleton
class NetworkDiagnosticsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkClient: NexusStreamNetworkClient
) {
    private val probeHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        // Легальные высокодоступные тестовые конечные точки (Cloudflare / Google) для замера задержки и скорости
        private const val LATENCY_PROBE_URL = "https://www.gstatic.com/generate_204"
        private const val SPEED_PROBE_URL = "https://speed.cloudflare.com/__down?bytes=2097152" // 2 МБ тестовый блок

        fun classifyNetworkReadiness(
            isConnected: Boolean,
            downloadSpeedMbps: Float,
            latencyMs: Long
        ): StreamQualityReadiness {
            if (!isConnected || downloadSpeedMbps <= 0.1f) return StreamQualityReadiness.OFFLINE
            return when {
                downloadSpeedMbps >= 15.0f && latencyMs < 250L -> StreamQualityReadiness.UHD_4K_READY
                downloadSpeedMbps >= 5.0f -> StreamQualityReadiness.HD_READY
                else -> StreamQualityReadiness.SD_ONLY
            }
        }

        fun buildUserAndEngineerSummary(
            isConnected: Boolean,
            transport: String,
            latencyMs: Long,
            speedMbps: Float,
            readiness: StreamQualityReadiness
        ): Pair<String, String> {
            val userText = when (readiness) {
                StreamQualityReadiness.OFFLINE ->
                    "Интернет-соединение отсутствует. Проверьте кабель Ethernet или подключение к Wi-Fi роутеру."
                StreamQualityReadiness.SD_ONLY ->
                    "Скорость интернета невысокая (${"%.1f".format(speedMbps)} Мбит/с). Каналы HD и 4K могут буферизироваться."
                StreamQualityReadiness.HD_READY ->
                    "Хорошее соединение (${"%.1f".format(speedMbps)} Мбит/с). Прямой эфир и фильмы в Full HD будут работать стабильно."
                StreamQualityReadiness.UHD_4K_READY ->
                    "Отличное соединение (${"%.1f".format(speedMbps)} Мбит/с, пинг ${latencyMs} мс). Доступны 4K UHD и режим Multiview."
            }
            val techText = "Transport=$transport | RTT=${latencyMs}ms | Throughput=${"%.2f".format(speedMbps)} Mbps | Verdict=${readiness.name}"
            return userText to techText
        }
    }

    suspend fun runInternetAndSpeedDiagnostic(): InternetDiagnosticSnapshot = withContext(Dispatchers.IO) {
        val transport = detectActiveNetworkTransport()
        if (transport == "Нет сети") {
            val (userMsg, techMsg) = buildUserAndEngineerSummary(false, transport, 0L, 0f, StreamQualityReadiness.OFFLINE)
            return@withContext InternetDiagnosticSnapshot(
                isInternetConnected = false,
                transportType = transport,
                latencyMs = 0L,
                downloadSpeedMbps = 0f,
                readinessVerdict = StreamQualityReadiness.OFFLINE,
                userSummary = userMsg,
                technicalDetails = techMsg
            )
        }

        // 1. Замер задержки соединения (HTTP RTT)
        val latencyMs = measureEndpointLatencyMs(LATENCY_PROBE_URL)

        // 2. Замер скорости скачивания тестового блока (2 МБ)
        val speedMbps = measureDownloadThroughputMbps(SPEED_PROBE_URL)

        val isConnected = latencyMs >= 0L || speedMbps > 0f
        val safeLatency = if (latencyMs >= 0L) latencyMs else 0L
        val readiness = classifyNetworkReadiness(isConnected, speedMbps, safeLatency)
        val (userMsg, techMsg) = buildUserAndEngineerSummary(isConnected, transport, safeLatency, speedMbps, readiness)

        InternetDiagnosticSnapshot(
            isInternetConnected = isConnected,
            transportType = transport,
            latencyMs = safeLatency,
            downloadSpeedMbps = speedMbps,
            readinessVerdict = readiness,
            userSummary = userMsg,
            technicalDetails = techMsg
        )
    }

    /**
     * Комплексная проверка IPTV-источника и его телепрограммы EPG («Проверить источник» — п. 17 ТЗ).
     */
    suspend fun checkIptvSourceAndEpg(source: SourceEntity): SourceProbeDiagnosticResult = withContext(Dispatchers.IO) {
        val playlistReportResult = networkClient.verifySourceReachability(
            url = source.endpointUrl,
            userAgent = source.userAgent
        )

        val playlistReachable = (playlistReportResult as? NexusResult.Success)?.data?.isReachable == true
        val playlistHttp = (playlistReportResult as? NexusResult.Success)?.data?.httpStatusCode ?: 0
        val playlistTime = (playlistReportResult as? NexusResult.Success)?.data?.latencyMs ?: 0L

        val epgUrl = source.epgUrl
        var epgConfigured = !epgUrl.isNullOrBlank()
        var epgReachable = false
        var epgHttp = 0
        var epgTime = 0L

        if (epgConfigured && epgUrl != null) {
            val epgReportResult = networkClient.verifySourceReachability(
                url = epgUrl,
                userAgent = source.userAgent
            )
            epgReachable = (epgReportResult as? NexusResult.Success)?.data?.isReachable == true
            epgHttp = (epgReportResult as? NexusResult.Success)?.data?.httpStatusCode ?: 0
            epgTime = (epgReportResult as? NexusResult.Success)?.data?.latencyMs ?: 0L
        }

        val userMessage = when {
            !playlistReachable -> {
                val errDetails = playlistReportResult.exceptionOrNull()?.message ?: "HTTP $playlistHttp"
                "Источник «${source.name}» недоступен: $errDetails"
            }
            epgConfigured && !epgReachable ->
                "Плейлист «${source.name}» работает (${playlistTime} мс), но сервер телепрограммы EPG не отвечает (HTTP $epgHttp)."
            !epgConfigured ->
                "Плейлист «${source.name}» доступен (${playlistTime} мс). Ссылка XMLTV EPG не задана."
            else ->
                "Источник «${source.name}» и телепрограмма EPG полностью исправны (отклик ${playlistTime} мс / ${epgTime} мс)."
        }

        val redactedUrl = UrlRedactor.redact(source.endpointUrl)
        val engineerLine = "Source[${source.id}:${source.name}] URL=$redactedUrl | PlaylistOK=$playlistReachable (HTTP $playlistHttp, ${playlistTime}ms) | EPG_OK=$epgReachable (HTTP $epgHttp, ${epgTime}ms)"

        SourceProbeDiagnosticResult(
            sourceId = source.id,
            sourceName = source.name,
            isPlaylistReachable = playlistReachable,
            playlistHttpCode = playlistHttp,
            playlistConnectTimeMs = playlistTime,
            isEpgConfigured = epgConfigured,
            isEpgReachable = epgReachable,
            epgHttpCode = epgHttp,
            epgConnectTimeMs = epgTime,
            userFriendlyMessage = userMessage,
            engineerLogLine = engineerLine
        )
    }

    private fun detectActiveNetworkTransport(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Неизвестно"
            val network = cm.activeNetwork ?: return "Нет сети"
            val caps = cm.getNetworkCapabilities(network) ?: return "Нет сети"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet (Кабель)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Мобильная сеть (LTE/5G)"
                else -> "Активное подключение"
            }
        } catch (_: Exception) {
            "Активное подключение"
        }
    }

    private fun measureEndpointLatencyMs(url: String): Long {
        return try {
            val startNs = System.nanoTime()
            val req = Request.Builder().url(url).head().build()
            probeHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 204) {
                    (System.nanoTime() - startNs) / 1_000_000L
                } else -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun measureDownloadThroughputMbps(url: String): Float {
        return try {
            val startNs = System.nanoTime()
            val req = Request.Builder().url(url).get().build()
            probeHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return 0f
                val stream = resp.body?.byteStream() ?: return 0f
                val buffer = ByteArray(16 * 1024)
                var totalBytes = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                }
                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                val bits = totalBytes * 8.0
                ((bits / elapsedSec) / 1_000_000.0).toFloat()
            }
        } catch (_: Exception) {
            0f
        }
    }
}
