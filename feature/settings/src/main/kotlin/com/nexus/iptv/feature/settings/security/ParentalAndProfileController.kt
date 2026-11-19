package com.nexus.iptv.feature.settings.security

import kotlinx.serialization.Serializable

/**
 * Профиль пользователя (п. 19 ТЗ — Profile System: несколько пользовательских профилей).
 */
@Serializable
data class UserProfileConfig(
    val id: Long,
    val name: String,
    val isKidsProfile: Boolean = false,
    val allowedCategoriesOnly: Set<String> = emptySet(),
    val startupChannelId: Long? = null
)

/**
 * Назначение аппаратных кнопок пульта ДУ (п. 19 ТЗ — Remote Customization).
 */
enum class CustomizableRemoteButton(val titleRu: String) {
    COLOR_RED("Красная кнопка ПДУ"),
    COLOR_GREEN("Зелёная кнопка ПДУ"),
    COLOR_YELLOW("Жёлтая кнопка ПДУ"),
    COLOR_BLUE("Синяя кнопка ПДУ"),
    LONG_OK("Долгое нажатие OK")
}

enum class RemoteCustomAction(val titleRu: String) {
    TOGGLE_FAVORITE("Добавить / убрать Избранное"),
    OPEN_EPG_GUIDE("Открыть телепрограмму EPG"),
    OPEN_QUICK_SWITCH("Открыть Quick Switch (Последние)"),
    CYCLE_ASPECT_RATIO("Изменить соотношение сторон"),
    START_DVR_RECORDING("Начать запись эфира (DVR)"),
    OPEN_MULTIVIEW("Открыть режим Multiview")
}

/**
 * Контроллер безопасности и автоматической блокировки настроек PIN-кодом (Этап 13, п. 16 ТЗ).
 */
class SettingsSecuritySessionGuard(
    private val autoLockTimeoutMs: Long = 5 * 60_000L // Автоблокировка настроек через 5 минут бездействия
) {
    private var lastUnlockedEpochMs: Long = 0L

    fun isUnlocked(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (lastUnlockedEpochMs == 0L) return false
        return (nowEpochMs - lastUnlockedEpochMs) <= autoLockTimeoutMs
    }

    fun tryUnlock(enteredPin: String, expectedPin: String, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (enteredPin.isNotBlank() && enteredPin == expectedPin) {
            lastUnlockedEpochMs = nowEpochMs
            return true
        }
        return false
    }

    fun lockImmediately() {
        lastUnlockedEpochMs = 0L
    }
}
