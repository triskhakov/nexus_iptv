package com.nexus.iptv.core.network.client

import com.nexus.iptv.core.common.result.NexusException
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.common.result.runNexusCatching
import com.nexus.iptv.core.common.security.UrlRedactor
import com.nexus.iptv.core.network.xtream.XtreamAuthResponse
import com.nexus.iptv.core.network.xtream.XtreamCategoryDto
import com.nexus.iptv.core.network.xtream.XtreamLiveStreamDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SourceAvailabilityReport(
    val isReachable: Boolean,
    val httpStatusCode: Int,
    val latencyMs: Long,
    val contentType: String?,
    val message: String
)

@Singleton
class NexusStreamNetworkClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        const val DEFAULT_USER_AGENT = "NEXUS-IPTV/1.0 (Android; Media3)"
    }

    suspend fun <T> executeStreamingReader(
        url: String,
        customUserAgent: String? = null,
        headers: Map<String, String> = emptyMap(),
        consumer: suspend (BufferedReader) -> T
    ): NexusResult<T> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val redactedUrl = UrlRedactor.redact(url)
            val requestBuilder = Request.Builder()
                .url(url.trim())
                .header("User-Agent", customUserAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT)
                .header("Accept-Encoding", "gzip, deflate")

            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            try {
                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 401 || response.code == 403) {
                        throw NexusException.UnauthorizedSourceException(redactedUrl)
                    }
                    if (!response.isSuccessful) {
                        throw NexusException.HttpStatusException(response.code, redactedUrl)
                    }
                    val body = response.body
                        ?: throw NexusException.HttpStatusException(204, redactedUrl)

                    val rawStream: InputStream = body.byteStream()
                    val isGzipped = response.header("Content-Encoding")?.contains("gzip", ignoreCase = true) == true ||
                        url.endsWith(".gz", ignoreCase = true)

                    val decodedStream = if (isGzipped) GZIPInputStream(rawStream) else rawStream
                    BufferedReader(InputStreamReader(decodedStream, Charsets.UTF_8), 32 * 1024).use { reader ->
                        consumer(reader)
                    }
                }
            } catch (e: SocketTimeoutException) {
                throw NexusException.ConnectionTimeoutException(redactedUrl, 15_000L, e)
            } catch (e: UnknownHostException) {
                throw NexusException.NetworkUnavailableException(e)
            }
        }
    }

    suspend fun verifySourceReachability(
        url: String,
        userAgent: String? = null
    ): NexusResult<SourceAvailabilityReport> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val redactedUrl = UrlRedactor.redact(url)
            val startNs = System.nanoTime()
            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", userAgent ?: DEFAULT_USER_AGENT)
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
                    SourceAvailabilityReport(
                        isReachable = response.isSuccessful,
                        httpStatusCode = response.code,
                        latencyMs = latencyMs,
                        contentType = response.header("Content-Type"),
                        message = if (response.isSuccessful) {
                            "Источник доступен (${latencyMs} мс, HTTP ${response.code})"
                        } else {
                            "Ошибка ответа сервера: HTTP ${response.code} для $redactedUrl"
                        }
                    )
                }
            } catch (e: SocketTimeoutException) {
                throw NexusException.ConnectionTimeoutException(redactedUrl, 15_000L, e)
            } catch (e: UnknownHostException) {
                throw NexusException.NetworkUnavailableException(e)
            }
        }
    }

    suspend fun authenticateXtream(
        baseUrl: String,
        username: String,
        passwordPlain: String
    ): NexusResult<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val endpoint = buildXtreamApiUrl(baseUrl, username, passwordPlain, action = null)
            val bodyString = executeJsonGet(endpoint, baseUrl)
            val authResponse = json.decodeFromString(XtreamAuthResponse.serializer(), bodyString)
            val userInfo = authResponse.userInfo
            if (userInfo == null || userInfo.auth == 0 || userInfo.status.equals("Expired", ignoreCase = true)) {
                throw NexusException.UnauthorizedSourceException(UrlRedactor.redact(baseUrl))
            }
            authResponse
        }
    }

    suspend fun fetchXtreamLiveCategories(
        baseUrl: String,
        username: String,
        passwordPlain: String
    ): NexusResult<List<XtreamCategoryDto>> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val endpoint = buildXtreamApiUrl(baseUrl, username, passwordPlain, action = "get_live_categories")
            val bodyString = executeJsonGet(endpoint, baseUrl)
            json.decodeFromString(ListSerializer(XtreamCategoryDto.serializer()), bodyString)
        }
    }

    suspend fun fetchXtreamLiveStreams(
        baseUrl: String,
        username: String,
        passwordPlain: String
    ): NexusResult<List<XtreamLiveStreamDto>> = withContext(Dispatchers.IO) {
        runNexusCatching {
            val endpoint = buildXtreamApiUrl(baseUrl, username, passwordPlain, action = "get_live_streams")
            val bodyString = executeJsonGet(endpoint, baseUrl)
            json.decodeFromString(ListSerializer(XtreamLiveStreamDto.serializer()), bodyString)
        }
    }

    private fun buildXtreamApiUrl(
        baseUrl: String,
        username: String,
        passwordPlain: String,
        action: String?
    ): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val parsed = "$cleanBase/player_api.php".toHttpUrlOrNull()
            ?: throw NexusException.PlaylistMalformedException(0, "Некорректный адрес сервера Xtream: ${UrlRedactor.redact(baseUrl)}")

        return parsed.newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("password", passwordPlain)
            .apply {
                if (!action.isNullOrBlank()) {
                    addQueryParameter("action", action)
                }
            }
            .build()
            .toString()
    }

    private fun executeJsonGet(fullUrl: String, sourceDisplayName: String): String {
        val redacted = UrlRedactor.redact(fullUrl)
        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw NexusException.UnauthorizedSourceException(UrlRedactor.redact(sourceDisplayName))
                }
                if (!response.isSuccessful) {
                    throw NexusException.HttpStatusException(response.code, redacted)
                }
                return response.body?.string()
                    ?: throw NexusException.HttpStatusException(204, redacted)
            }
        } catch (e: SocketTimeoutException) {
            throw NexusException.ConnectionTimeoutException(redacted, 15_000L, e)
        } catch (e: UnknownHostException) {
            throw NexusException.NetworkUnavailableException(e)
        }
    }
}
