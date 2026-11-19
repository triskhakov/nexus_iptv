package com.nexus.iptv.core.common.result

import kotlinx.coroutines.CancellationException

sealed interface NexusResult<out T> {
    data class Success<T>(val data: T) : NexusResult<T>
    data class Error(val exception: NexusException) : NexusResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): NexusException? = (this as? Error)?.exception
}

inline fun <T, R> NexusResult<T>.map(transform: (T) -> R): NexusResult<R> = when (this) {
    is NexusResult.Success -> NexusResult.Success(transform(data))
    is NexusResult.Error -> this
}

inline fun <T> runNexusCatching(block: () -> T): NexusResult<T> {
    return try {
        NexusResult.Success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (ne: NexusException) {
        NexusResult.Error(ne)
    } catch (t: Throwable) {
        NexusResult.Error(NexusException.UnexpectedException(t.message ?: "Неизвестная системная ошибка", t))
    }
}

sealed class NexusException(
    override val message: String,
    override val cause: Throwable? = null,
    val diagnosticCode: String
) : Exception(message, cause) {
    class NetworkUnavailableException(cause: Throwable? = null) :
        NexusException("Отсутствует подключение к сети Интернет", cause, "NET_001")
    class ConnectionTimeoutException(val urlRedacted: String, val timeoutMs: Long, cause: Throwable? = null) :
        NexusException("Превышено время ожидания ответа ($timeoutMs мс) от источника $urlRedacted", cause, "NET_002")
    class HttpStatusException(val statusCode: Int, val urlRedacted: String) :
        NexusException("Сервер вернул код HTTP $statusCode для ресурса $urlRedacted", null, "HTTP_$statusCode")
    class UnauthorizedSourceException(val sourceName: String) :
        NexusException("Ошибка авторизации источника «$sourceName»: проверьте логин, пароль или срок подписки", null, "AUTH_401")
    class PlaylistMalformedException(val line: Int, val details: String, cause: Throwable? = null) :
        NexusException("Повреждённый формат плейлиста (строка $line): $details", cause, "PARSE_M3U_001")
    class XmltvMalformedException(val details: String, cause: Throwable? = null) :
        NexusException("Ошибка структуры XMLTV EPG: $details", cause, "PARSE_EPG_001")
    class DecoderInitializationException(val codecName: String?, val isHardware: Boolean, cause: Throwable? = null) :
        NexusException("Сбой инициализации декодера (${codecName ?: "unknown"}, HW=$isHardware)", cause, "PLAY_DEC_001")
    class StreamStalledException(val stallDurationMs: Long) :
        NexusException("Поток завис: буферизация более $stallDurationMs мс", null, "PLAY_BUF_002")
    class StorageInsufficientException(val requiredBytes: Long, val availableBytes: Long) :
        NexusException("Недостаточно памяти для записи/таймшифта (доступно ${availableBytes / 1048576} МБ)", null, "STOR_001")
    class SecurityVaultException(details: String, cause: Throwable? = null) :
        NexusException("Ошибка криптографического хранилища: $details", cause, "SEC_001")
    class UnexpectedException(details: String, cause: Throwable? = null) :
        NexusException(details, cause, "SYS_999")
}
