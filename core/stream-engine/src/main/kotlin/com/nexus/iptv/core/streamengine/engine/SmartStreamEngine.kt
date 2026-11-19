package com.nexus.iptv.core.streamengine.engine

import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.player.contract.DecoderMode
import com.nexus.iptv.core.player.contract.PlayerState
import com.nexus.iptv.core.player.contract.VideoPlayerEngine
import com.nexus.iptv.core.streamengine.policy.SmartStreamConfig
import com.nexus.iptv.core.streamengine.policy.SmartStreamMath
import com.nexus.iptv.core.streamengine.policy.SmartStreamStatus
import com.nexus.iptv.core.streamengine.policy.StreamErrorRecord
import com.nexus.iptv.core.streamengine.policy.StreamHealthVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart Stream Engine — интеллектуальный супервизор качества и отказоустойчивости потоков (Этап 11, п. 9 и п. 19 ТЗ).
 *
 * Реализует все 10 задач п. 9 ТЗ:
 * 1. Непрерывно контролирует состояние потока (`PlayerState`) и телеметрию (`PlaybackTelemetry`);
 * 2. Измеряет время начала воспроизведения и записывает его в статистику канала;
 * 3. Обнаруживает зависание буфера (`Stall Watchdog`, если буферизация длится дольше `stallWatchdogThresholdMs`);
 * 4. Обнаруживает потерю соединения и повторяющиеся ошибки;
 * 5. Анализирует буферизацию и оценивает скорость загрузки;
 * 6. Автоматически находит альтернативные варианты того же канала (`findAlternativeStreams` по `unifiedGroupKey`);
 * 7. Выполняет повторное подключение с экспоненциальной задержкой и жёстким лимитом циклов (защита от бесконечных реконнектов);
 * 8. Сохраняет историю ошибок (`recentErrors`) и обновляет `Channel Health Score` в базе данных;
 * 9. Никогда не переключает качество видео без согласия пользователя, если поток не является адаптивным ABR.
 */
@Singleton
class SmartStreamEngine @Inject constructor(
    private val playerEngine: VideoPlayerEngine,
    private val nexusDao: NexusDao
) {
    private val supervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var config: SmartStreamConfig = SmartStreamConfig()
    private var primaryChannel: ChannelEntity? = null
    private var currentlyPlayingChannel: ChannelEntity? = null
    private var currentDecoderMode: DecoderMode = DecoderMode.AUTO

    private var monitorJob: Job? = null
    private var recoveryJob: Job? = null
    private var totalRecoveryCyclesCount = 0
    private var currentStreamRetryCount = 0
    private var bufferingStartedAtMs: Long? = null
    private val triedBackupChannelIds = LinkedHashSet<Long>()

    private val _status = MutableStateFlow(SmartStreamStatus())
    val status: StateFlow<SmartStreamStatus> = _status.asStateFlow()

    fun updateConfiguration(newConfig: SmartStreamConfig) {
        config = newConfig
    }

    /**
     * Запускает воспроизведение канала под контролем Smart Stream Engine.
     */
    fun startSupervisedPlayback(
        channel: ChannelEntity,
        decoderMode: DecoderMode = DecoderMode.AUTO
    ) {
        recoveryJob?.cancel()
        monitorJob?.cancel()

        primaryChannel = channel
        currentlyPlayingChannel = channel
        currentDecoderMode = decoderMode
        totalRecoveryCyclesCount = 0
        currentStreamRetryCount = 0
        bufferingStartedAtMs = System.currentTimeMillis()
        triedBackupChannelIds.clear()
        triedBackupChannelIds.add(channel.id)

        _status.update {
            it.copy(
                activeChannelId = channel.id,
                activeChannelName = channel.name,
                isUsingBackupStream = false,
                verdict = StreamHealthVerdict.HEALTHY,
                retryAttempt = 0,
                maxRetries = config.maxRetriesPerStream,
                consecutiveFailures = 0,
                computedHealthScore = channel.healthScore,
                diagnosticHint = "Подключение к потоку..."
            )
        }

        // Гарантируем, что качество не занижается принудительно без согласия пользователя (п. 9 ТЗ)
        if (!config.allowQualityDowngradeWithoutConsent) {
            playerEngine.selectVideoQuality(null)
        }

        playerEngine.prepareAndPlay(
            streamUrl = channel.streamUrl,
            decoderMode = decoderMode
        )

        startTelemetryAndWatchdogLoop()
    }

    private fun startTelemetryAndWatchdogLoop() {
        monitorJob = supervisorScope.launch {
            while (isActive) {
                val state = playerEngine.playerState.value
                val telemetry = playerEngine.telemetry.value
                val activeCh = currentlyPlayingChannel ?: break

                when (state) {
                    is PlayerState.Ready -> {
                        bufferingStartedAtMs = null
                        if (state.isPlaying) {
                            currentStreamRetryCount = 0
                            val updatedHealth = SmartStreamMath.calculateChannelHealthScore(
                                startupDurationMs = telemetry.startupDurationMs,
                                rebufferCount = telemetry.rebufferCount,
                                failureCount = activeCh.failureCount,
                                droppedFramesCount = telemetry.droppedFramesCount
                            )

                            _status.update {
                                it.copy(
                                    verdict = if (it.isUsingBackupStream) StreamHealthVerdict.SWITCHED_TO_BACKUP else StreamHealthVerdict.HEALTHY,
                                    retryAttempt = 0,
                                    consecutiveFailures = 0,
                                    lastStartupDurationMs = telemetry.startupDurationMs,
                                    estimatedDownloadSpeedKbps = telemetry.currentBitrateKbps,
                                    computedHealthScore = updatedHealth,
                                    diagnosticHint = if (it.isUsingBackupStream) {
                                        "Воспроизводится резервный источник (${activeCh.name})"
                                    } else {
                                        "Поток стабилен (${telemetry.currentBitrateKbps} кбит/с, старт ${telemetry.startupDurationMs} мс)"
                                    }
                                )
                            }

                            // Периодически фиксируем реальный рейтинг стабильности канала в БД
                            if (telemetry.startupDurationMs > 0L) {
                                nexusDao.updateChannelHealthMetrics(
                                    channelId = activeCh.id,
                                    newHealthScore = updatedHealth,
                                    avgStartupMs = telemetry.startupDurationMs,
                                    failureCount = activeCh.failureCount
                                )
                            }
                        }
                    }

                    is PlayerState.Buffering -> {
                        val now = System.currentTimeMillis()
                        val bufStart = bufferingStartedAtMs ?: now.also { bufferingStartedAtMs = it }
                        val stalledMs = now - bufStart

                        if (stalledMs >= config.stallWatchdogThresholdMs) {
                            bufferingStartedAtMs = null
                            triggerRecoverySequence(
                                errorCode = "STALL_WATCHDOG_${stalledMs}MS",
                                rawMessage = "Зависание буферизации более ${stalledMs} мс"
                            )
                        } else {
                            _status.update {
                                it.copy(
                                    verdict = StreamHealthVerdict.DEGRADED_BUFFERING,
                                    diagnosticHint = "Буферизация потока (${state.percent}%, ожидание ${stalledMs / 1000} с)..."
                                )
                            }
                        }
                    }

                    is PlayerState.Failed -> {
                        bufferingStartedAtMs = null
                        triggerRecoverySequence(
                            errorCode = state.errorCode,
                            rawMessage = state.message
                        )
                    }

                    is PlayerState.Idle,
                    is PlayerState.Ended -> {
                        // Ожидание команды пользователя
                    }
                }

                delay(1_000L)
            }
        }
    }

    /**
     * Выполняет интеллектуальное восстановление потока:
     * 1. Проверяет жёсткий лимит циклов (`maxTotalRecoveryCycles`), предотвращая бесконечный цикл;
     * 2. Повторяет подключение к текущему потоку с экспоненциальной задержкой (`computeExponentialBackoffMs`);
     * 3. Если текущий поток исчерпал лимит попыток (`maxRetriesPerStream`), переключается на резервный канал
     *    с наивысшим `Channel Health Score`.
     */
    private fun triggerRecoverySequence(errorCode: String, rawMessage: String) {
        if (recoveryJob?.isActive == true) return
        val activeCh = currentlyPlayingChannel ?: return

        recoveryJob = supervisorScope.launch {
            totalRecoveryCyclesCount++
            currentStreamRetryCount++

            val newFailureCount = activeCh.failureCount + 1
            val degradedScore = SmartStreamMath.calculateChannelHealthScore(
                startupDurationMs = playerEngine.telemetry.value.startupDurationMs,
                rebufferCount = playerEngine.telemetry.value.rebufferCount + 1,
                failureCount = newFailureCount,
                droppedFramesCount = playerEngine.telemetry.value.droppedFramesCount
            )
            nexusDao.updateChannelHealthMetrics(
                channelId = activeCh.id,
                newHealthScore = degradedScore,
                avgStartupMs = activeCh.avgStartupTimeMs,
                failureCount = newFailureCount
            )

            val telemetry = playerEngine.telemetry.value
            val diagnosis = SmartStreamMath.diagnoseNetworkIssue(
                errorCode = errorCode,
                bufferedMs = telemetry.bufferedDurationMs,
                bitrateKbps = telemetry.currentBitrateKbps
            )

            // 1. Защита от бесконечных циклов переподключения (п. 9 ТЗ)
            if (totalRecoveryCyclesCount > config.maxTotalRecoveryCycles) {
                recordError(activeCh, errorCode, diagnosis, "Остановка: исчерпан общий лимит попыток восстановления")
                _status.update {
                    it.copy(
                        verdict = StreamHealthVerdict.EXHAUSTED_UNAVAILABLE,
                        computedHealthScore = degradedScore,
                        diagnosticHint = "Источник временно недоступен после ${config.maxTotalRecoveryCycles} попыток. $diagnosis"
                    )
                }
                return@launch
            }

            // 2. Если текущий URL исчерпал локальный лимит попыток -> пробуем резервный поток (Failover)
            if (currentStreamRetryCount > config.maxRetriesPerStream && config.enableAutoBackupFailover) {
                val rootChannel = primaryChannel ?: activeCh
                val alternatives = nexusDao.findAlternativeStreams(
                    unifiedKey = rootChannel.unifiedGroupKey,
                    excludeChannelId = activeCh.id
                ).filter { it.id !in triedBackupChannelIds }

                val bestBackup = alternatives.firstOrNull()
                if (bestBackup != null) {
                    triedBackupChannelIds.add(bestBackup.id)
                    currentlyPlayingChannel = bestBackup
                    currentStreamRetryCount = 0
                    bufferingStartedAtMs = System.currentTimeMillis()

                    recordError(
                        activeCh,
                        errorCode,
                        diagnosis,
                        "Автопереключение на резервный поток: ${bestBackup.name} (Health ${bestBackup.healthScore}%)"
                    )

                    _status.update {
                        it.copy(
                            activeChannelId = bestBackup.id,
                            activeChannelName = bestBackup.name,
                            isUsingBackupStream = true,
                            verdict = StreamHealthVerdict.SWITCHED_TO_BACKUP,
                            retryAttempt = 1,
                            computedHealthScore = bestBackup.healthScore,
                            diagnosticHint = "Переключение на резервный поток «${bestBackup.name}»..."
                        )
                    }

                    playerEngine.prepareAndPlay(
                        streamUrl = bestBackup.streamUrl,
                        decoderMode = currentDecoderMode
                    )
                    return@launch
                }
            }

            // 3. Экспоненциальная задержка перед повторным подключением к активному потоку
            val backoffDelayMs = SmartStreamMath.computeExponentialBackoffMs(currentStreamRetryCount, config)
            recordError(
                activeCh,
                errorCode,
                diagnosis,
                "Повтор #$currentStreamRetryCount через ${backoffDelayMs} мс"
            )

            _status.update {
                it.copy(
                    verdict = StreamHealthVerdict.STALLED_RECONNECTING,
                    retryAttempt = currentStreamRetryCount,
                    maxRetries = config.maxRetriesPerStream,
                    consecutiveFailures = newFailureCount,
                    computedHealthScore = degradedScore,
                    diagnosticHint = "Переподключение (${currentStreamRetryCount}/${config.maxRetriesPerStream}) через ${backoffDelayMs / 1000.0} с... $diagnosis"
                )
            }

            delay(backoffDelayMs)
            bufferingStartedAtMs = System.currentTimeMillis()
            playerEngine.prepareAndPlay(
                streamUrl = activeCh.streamUrl,
                decoderMode = currentDecoderMode
            )
        }
    }

    private fun recordError(
        channel: ChannelEntity,
        errorCode: String,
        diagnosticMessage: String,
        actionTaken: String
    ) {
        val entry = StreamErrorRecord(
            timestampEpochMs = System.currentTimeMillis(),
            channelId = channel.id,
            channelName = channel.name,
            errorCode = errorCode,
            diagnosticMessage = diagnosticMessage,
            recoveryActionTaken = actionTaken
        )
        _status.update { current ->
            current.copy(recentErrors = (listOf(entry) + current.recentErrors).take(25))
        }
    }

    fun stopSupervision() {
        recoveryJob?.cancel()
        monitorJob?.cancel()
        bufferingStartedAtMs = null
    }
}
