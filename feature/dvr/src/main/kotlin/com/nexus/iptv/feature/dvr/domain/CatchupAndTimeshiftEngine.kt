package com.nexus.iptv.feature.dvr.domain

import com.nexus.iptv.core.database.entity.ChannelEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Результат честной проверки доступности функций Timeshift, Catch-up и DVR (Этап 9, п. 10 ТЗ).
 *
 * Важное условие ТЗ:
 * «Timeshift, Catch-up и DVR должны работать только при наличии технической поддержки источника,
 * разрешённого доступа и необходимых системных возможностей. Не имитируй наличие функций, если они недоступны.»
 */
data class StreamTimeCapabilities(
    val isCatchupSupported: Boolean,
    val catchupReason: String,
    val isLiveTimeshiftSupported: Boolean,
    val timeshiftReason: String,
    val isDvrRecordingSupported: Boolean,
    val dvrReason: String
)

object CatchupAndTimeshiftEngine {

    private const val MIN_TIMESHIFT_FREE_BYTES = 150L * 1024L * 1024L // Минимум 150 МБ для буфера паузы эфира
    private const val MIN_DVR_FREE_BYTES = 500L * 1024L * 1024L       // Минимум 500 МБ для записи передач

    /**
     * Проверяет реальную техническую доступность Catch-up, Timeshift и DVR без имитации.
     */
    fun evaluateCapabilities(
        channel: ChannelEntity,
        availableStorageBytes: Long,
        isStorageWritable: Boolean
    ): StreamTimeCapabilities {
        val streamLower = channel.streamUrl.lowercase()
        val isHttpOrHlsOrTs = streamLower.startsWith("http://") || streamLower.startsWith("https://")

        // 1. Проверка Catch-up (Архив телепередач провайдера)
        val hasValidCatchupMeta = channel.catchupDays > 0 && !channel.catchupType.isNullOrBlank()
        val catchupSupported = hasValidCatchupMeta && isHttpOrHlsOrTs
        val catchupMsg = when {
            !isHttpOrHlsOrTs -> "Протокол потока не поддерживает HTTP-архивы Catch-up"
            !hasValidCatchupMeta -> "Провайдер не предоставляет архив (catchup-days=0)"
            else -> "Архив доступен за ${channel.catchupDays} дн. (${channel.catchupType})"
        }

        // 2. Проверка Live Timeshift (Пауза и перемотка прямого эфира через локальный кольцевой буфер)
        val timeshiftSupported = isHttpOrHlsOrTs && isStorageWritable && availableStorageBytes >= MIN_TIMESHIFT_FREE_BYTES
        val timeshiftMsg = when {
            !isHttpOrHlsOrTs -> "Локальный буфер Timeshift поддерживается только для HTTP/HLS/MPEG-TS потоков"
            !isStorageWritable -> "Хранилище устройства недоступно для записи буфера Timeshift"
            availableStorageBytes < MIN_TIMESHIFT_FREE_BYTES ->
                "Недостаточно свободной памяти для Timeshift (требуется >= 150 МБ, доступно ${availableStorageBytes / 1048576} МБ)"
            else -> "Пауза и перемотка прямого эфира активны"
        }

        // 3. Проверка DVR (Запись эфира на накопитель)
        val dvrSupported = isHttpOrHlsOrTs && isStorageWritable && availableStorageBytes >= MIN_DVR_FREE_BYTES
        val dvrMsg = when {
            !isHttpOrHlsOrTs -> "Запись DVR поддерживается только для HTTP/HTTPS потоков"
            !isStorageWritable -> "Отсутствует доступ на запись в хранилище устройства"
            availableStorageBytes < MIN_DVR_FREE_BYTES ->
                "Недостаточно места для записи DVR (требуется >= 500 МБ, доступно ${availableStorageBytes / 1048576} МБ)"
            else -> "Запись передач (DVR) доступна"
        }

        return StreamTimeCapabilities(
            isCatchupSupported = catchupSupported,
            catchupReason = catchupMsg,
            isLiveTimeshiftSupported = timeshiftSupported,
            timeshiftReason = timeshiftMsg,
            isDvrRecordingSupported = dvrSupported,
            dvrReason = dvrMsg
        )
    }

    /**
     * Строит точный URL для просмотра прошедшей передачи из архива провайдера (Catch-up).
     *
     * Поддерживает все промышленные стандарты IPTV:
     * - DEFAULT / TEMPLATE: полная замена макросов {utc}, ${utc}, {start}, ${start}, {end}, {duration}, {offset}, ${timestamp}, {Y}{m}{d}{H}{M}{S};
     * - APPEND: добавление шаблона catchup-source к базовому streamUrl канала;
     * - SHIFT / SIPTV: добавление ?utc={startSec}&lutc={nowSec};
     * - FLUSSONIC: автоматическая трансформация /index.m3u8 -> /index-{startSec}-{durationSec}.m3u8 или /archive-{startSec}-{durationSec}.ts;
     * - XTREAM: формирование ссылки /timeshift/{user}/{pass}/{durationMin}/{YYYY-MM-dd:HH-mm}/{streamId}.ts.
     */
    fun buildCatchupUrl(
        channel: ChannelEntity,
        programStartEpochMs: Long,
        programEndEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): String? {
        if (channel.catchupDays <= 0 || channel.catchupType.isNullOrBlank()) return null

        // Проверяем, не вышла ли передача за пределы глубины архива провайдера
        val maxArchiveAgeMs = channel.catchupDays * 86_400_000L
        if (nowEpochMs - programStartEpochMs > maxArchiveAgeMs || programStartEpochMs >= nowEpochMs) {
            return null
        }

        val cleanStreamUrl = channel.streamUrl.substringBefore('|')
        val inlineModifiers = if (channel.streamUrl.contains('|')) {
            "|" + channel.streamUrl.substringAfter('|')
        } else ""

        val startSec = programStartEpochMs / 1000L
        val endSec = programEndEpochMs / 1000L
        val nowSec = nowEpochMs / 1000L
        val durationSec = (endSec - startSec).coerceAtLeast(60L)
        val durationMin = (durationSec / 60L).coerceAtLeast(1L)
        val offsetSec = (nowSec - startSec).coerceAtLeast(0L)

        val type = channel.catchupType!!.uppercase(Locale.ROOT)
        val template = channel.catchupSourceTemplate

        val resolvedBaseUrl = when (type) {
            "DEFAULT", "TEMPLATE" -> {
                if (template.isNullOrBlank()) return null
                expandCatchupMacros(template, startSec, endSec, nowSec, durationSec, offsetSec)
            }
            "APPEND" -> {
                if (template.isNullOrBlank()) return null
                val expandedSuffix = expandCatchupMacros(template, startSec, endSec, nowSec, durationSec, offsetSec)
                cleanStreamUrl + expandedSuffix
            }
            "SHIFT", "SIPTV" -> {
                val separator = if (cleanStreamUrl.contains('?')) "&" else "?"
                "${cleanStreamUrl}${separator}utc=$startSec&lutc=$nowSec"
            }
            "FLUSSONIC", "FS" -> {
                if (!template.isNullOrBlank()) {
                    expandCatchupMacros(template, startSec, endSec, nowSec, durationSec, offsetSec)
                } else {
                    buildFlussonicArchiveUrl(cleanStreamUrl, startSec, durationSec)
                }
            }
            "XTREAM", "XC" -> {
                buildXtreamTimeshiftUrl(cleanStreamUrl, programStartEpochMs, durationMin) ?: return null
            }
            else -> {
                if (!template.isNullOrBlank()) {
                    expandCatchupMacros(template, startSec, endSec, nowSec, durationSec, offsetSec)
                } else null
            }
        } ?: return null

        return resolvedBaseUrl + inlineModifiers
    }

    private fun expandCatchupMacros(
        template: String,
        startSec: Long,
        endSec: Long,
        nowSec: Long,
        durationSec: Long,
        offsetSec: Long
    ): String {
        val utcDate = Date(startSec * 1000L)
        val utcTz = TimeZone.getTimeZone("UTC")
        fun fmt(pattern: String): String = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utcTz }.format(utcDate)

        return template
            .replace("\${utc}", startSec.toString())
            .replace("{utc}", startSec.toString())
            .replace("\${start}", startSec.toString())
            .replace("{start}", startSec.toString())
            .replace("\${end}", endSec.toString())
            .replace("{end}", endSec.toString())
            .replace("\${timestamp}", nowSec.toString())
            .replace("{timestamp}", nowSec.toString())
            .replace("\${lutc}", nowSec.toString())
            .replace("{lutc}", nowSec.toString())
            .replace("\${duration}", durationSec.toString())
            .replace("{duration}", durationSec.toString())
            .replace("\${offset}", offsetSec.toString())
            .replace("{offset}", offsetSec.toString())
            .replace("\${Y}", fmt("yyyy"))
            .replace("{Y}", fmt("yyyy"))
            .replace("\${m}", fmt("MM"))
            .replace("{m}", fmt("MM"))
            .replace("\${d}", fmt("dd"))
            .replace("{d}", fmt("dd"))
            .replace("\${H}", fmt("HH"))
            .replace("{H}", fmt("HH"))
            .replace("\${M}", fmt("mm"))
            .replace("{M}", fmt("mm"))
            .replace("\${S}", fmt("ss"))
            .replace("{S}", fmt("ss"))
    }

    private fun buildFlussonicArchiveUrl(cleanUrl: String, startSec: Long, durationSec: Long): String {
        val basePart = cleanUrl.substringBefore('?')
        val queryPart = cleanUrl.substringAfter('?', "").let { if (it.isNotEmpty()) "?$it" else "" }
        return when {
            basePart.endsWith("/index.m3u8", ignoreCase = true) ->
                basePart.removeSuffix("/index.m3u8") + "/index-$startSec-$durationSec.m3u8$queryPart"
            basePart.endsWith(".m3u8", ignoreCase = true) ->
                basePart.removeSuffix(".m3u8") + "-$startSec-$durationSec.m3u8$queryPart"
            else ->
                basePart.trimEnd('/') + "/archive-$startSec-$durationSec.ts$queryPart"
        }
    }

    /**
     * Преобразует стандартный live-поток Xtream Codes:
     * http://host:port/live/USER/PASS/1042.ts -> http://host:port/timeshift/USER/PASS/{durationMin}/{yyyy-MM-dd:HH-mm}/1042.ts
     */
    private fun buildXtreamTimeshiftUrl(cleanUrl: String, startEpochMs: Long, durationMin: Long): String? {
        val regex = Regex("(https?://[^/]+)/live/([^/]+)/([^/]+)/([^/?]+)")
        val match = regex.find(cleanUrl) ?: return null
        val host = match.groupValues[1]
        val user = match.groupValues[2]
        val pass = match.groupValues[3]
        val streamFile = match.groupValues[4]

        val startFormatted = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(startEpochMs))

        return "$host/timeshift/$user/$pass/$durationMin/$startFormatted/$streamFile"
    }
}
