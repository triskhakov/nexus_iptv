package com.nexus.iptv.core.player.external

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import com.nexus.iptv.core.common.result.NexusException
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.common.result.runNexusCatching
import com.nexus.iptv.core.player.url.StreamUrlHeaderInjector

/**
 * Утилита интеграции видеоплеера с системными возможностями Android (Этап 5, п. 8 ТЗ):
 * 1. Запуск совместимого контента во внешнем проигрывателе (VLC, MX Player, Just Player);
 * 2. Вход в режим Picture-in-Picture (PiP) с сохранением пропорций видеокадра;
 * 3. Аппаратное управление яркостью экрана текущего окна воспроизведения.
 */
object ExternalPlayerLauncher {

    fun launchExternalPlayer(
        context: Context,
        streamUrl: String,
        channelTitle: String
    ): NexusResult<Unit> = runNexusCatching {
        val parsed = StreamUrlHeaderInjector.parse(streamUrl)
        val mimeType = parsed.detectedMimeType ?: "video/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(parsed.cleanUrl), mimeType)
            putExtra("title", channelTitle)
            putExtra(Intent.EXTRA_TITLE, channelTitle)
            if (parsed.headers.isNotEmpty()) {
                val headersArray = parsed.headers.flatMap { listOf(it.key, it.value) }.toTypedArray()
                putExtra("headers", headersArray)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val packageManager = context.packageManager
        if (intent.resolveActivity(packageManager) != null) {
            context.startActivity(Intent.createChooser(intent, "Открыть поток во внешнем плеере").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            throw NexusException.UnexpectedException("На устройстве не найдено совместимых внешних видеоплееров")
        }
    }

    fun enterPictureInPicture(
        activity: Activity,
        videoWidth: Int,
        videoHeight: Int
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val safeW = if (videoWidth in 100..7680) videoWidth else 16
        val safeH = if (videoHeight in 100..4320) videoHeight else 9
        val rational = Rational(safeW, safeH).let {
            // Android PiP ограничивает соотношение сторон диапазоном 0.4184..2.39
            if (it.toFloat() in 0.42f..2.38f) it else Rational(16, 9)
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .build()
        return activity.enterPictureInPictureMode(params)
    }

    fun applyWindowBrightness(activity: Activity, brightnessFraction: Float) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = brightnessFraction.coerceIn(0.05f, 1.0f)
        activity.window.attributes = layoutParams
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
