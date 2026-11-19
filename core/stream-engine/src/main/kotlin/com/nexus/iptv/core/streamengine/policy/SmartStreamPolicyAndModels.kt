package com.nexus.iptv.core.streamengine.policy

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Конфигурация интеллектуальной системы управления потоками — Smart Stream Engine (Этап 11, п. 9 ТЗ).
 */
data class SmartStreamConfig(
    val connectionTimeoutMs: Long = 12_000L,           // Настраиваемый таймаут подключения
    val stallWatchdogThresholdMs: Long = 7_500L,       // Порог обнаружения зависания буфера
    val initialRetryDelayMs: Long = 800L,              // Начальная задержка перед повтором
    val maxRetryDelayMs: Long = 16_000L,               // Максимальная задержка (потолок экспоненты)
    val backoffMultiplier: Double = 2.0,               // Множитель экспоненциальной задержки
    val maxRetriesPerStream: Int = 4,                  // Ограничение повторов на один URL
    val maxTotalRecoveryCycles: Int = 8,               // Жёсткая защита от бесконечных циклов переподключения
    val enableAutoBackupFailover: Boolean = true,      // Переключение на резервный поток при сбое
    val allowQualityDowngradeWithoutConsent: Boolean = false // Строгое соблюдение п. 9 ТЗ: не снижать качество без согласия
)

data class StreamErrorRecord(
    val timestampEpochMs: Long,
    val channelId: Long,
    val channelName: String,
    val errorCode: String,
    val diagnosticMessage: String,
    val recoveryActionTaken: String
)

enum class StreamHealthVerdict {
    HEALTHY,
    DEGRADED_BUFFERING,
    STALLED_RECONNECTING,
    SWITCHED_TO_BACKUP,
    EXHAUSTED_UNAVAILABLE
}

data class SmartStreamStatus(
    val activeChannelId: Long = 0L,
    val activeChannelName: String = "",
    val isUsingBackupStream: Boolean = false,
    val verdict: StreamHealthVerdict = StreamHealthVerdict.HEALTHY,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 4,
    val consecutiveFailures: Int = 0,
    val lastStartupDurationMs: Long = 0L,
    val estimatedDownloadSpeedKbps: Long = 0L,
    val computedHealthScore: Int = 100,
    val diagnosticHint: String = "Поток стабилен",
    val recentErrors: List<StreamErrorRecord> = emptyList()
)

/**
 * Алгоритмическое ядро расчёта экспоненциальной задержки (Exponential Backoff + Jitter),
 * оценки стабильности Channel Health Score (0..100) и диагностики сетевых проблем.
 */
object SmartStreamMath {

    /**
     * Вычисляет задержку повторного подключения по формуле:
     * delay = min(maxDelay, initialDelay * multiplier^(attempt - 1)) + jitter(0..250ms)
     */
    fun computeExponentialBackoffMs(
        attemptNumber: Int,
        config: SmartStreamConfig,
        jitterRandom: Random = Random.Default
    ): Long {
        if (attemptNumber <= 0) return 0L
        val exponent = (attemptNumber - 1).coerceIn(0, 10)
        val rawDelay = config.initialRetryDelayMs * config.backoffMultiplier.pow(exponent.toDouble())
        val cappedDelay = min(config.maxRetryDelayMs.toDouble(), rawDelay).toLong()
        val jitterMs = jitterRandom.nextLong(0L, 250L)
        return cappedDelay + jitterMs
    }

    /**
     * Рассчитывает индекс стабильности канала Channel Health Score (0..100) на основании реальной телеметрии:
     * - Базовый балл: 100;
     * - Штраф за медленный старт канала (> 1500 мс: до -20 баллов);
     * - Штраф за ребуферизации (-6 баллов за каждую остановку буфера, до -30 баллов);
     * - Штраф за сбои соединения (-15 баллов за каждый отказ, до -45 баллов);
     * - Штраф за высокий процент пропущенных кадров (-5 баллов).
     */
    fun calculateChannelHealthScore(
        startupDurationMs: Long,
        rebufferCount: Int,
        failureCount: Int,
        droppedFramesCount: Int
    ): Int {
        var score = 100

        // 1. Оценка времени запуска (быстрый старт < 1000 мс = 0 штрафа)
        if (startupDurationMs > 1_000L) {
            val startupPenalty = (((startupDurationMs - 1_000L) / 250L).toInt()).coerceIn(1, 20)
            score -= startupPenalty
        }

        // 2. Оценка стабильности буфера
        if (rebufferCount > 0) {
            score -= (rebufferCount * 6).coerceAtMost(30)
        }

        // 3. Оценка отказов и обрывов потока
        if (failureCount > 0) {
            score -= (failureCount * 15).coerceAtMost(45)
        }

        // 4. Оценка выпадения кадров декодера
        if (droppedFramesCount > 30) {
            score -= 5
        }

        return score.coerceIn(0, 100)
    }

    /**
     * Формирует понятную диагностику сетевой проблемы на основе кода ошибки Media3 и телеметрии.
     */
    fun diagnoseNetworkIssue(errorCode: String, bufferedMs: Long, bitrateKbps: Long): String {
        val codeUpper = errorCode.uppercase()
        return when {
            codeUpper.contains("TIMEOUT") || codeUpper.contains("IO_NETWORK_CONNECTION_TIMEOUT") ->
                "Таймаут соединения: сервер провайдера не отвечает вовремя. Проверьте пинг или переключитесь на резервный поток."
            codeUpper.contains("UNAVAILABLE") || codeUpper.contains("UNKNOWN_HOST") ->
                "Нет доступа к сети или ошибка DNS-резолвинга. Проверьте подключение устройства к интернету."
            codeUpper.contains("BAD_HTTP_STATUS") || codeUpper.contains("403") || codeUpper.contains("401") ->
                "Сервер отклонил запрос (HTTP 401/403). Возможен лимит одновременных подключений или истечение токена."
            codeUpper.contains("404") || codeUpper.contains("410") ->
                "Поток канала временно удалён или недоступен на сервере (HTTP 404)."
            bufferedMs < 500L && bitrateKbps > 0L ->
                "Недостаточная скорость загрузки для текущего битрейта (${bitrateKbps} кбит/с): буфер опустошён."
            else ->
                "Сбой воспроизведения ($errorCode). Выполняется интеллектуальное восстановление соединения."
        }
    }
}
