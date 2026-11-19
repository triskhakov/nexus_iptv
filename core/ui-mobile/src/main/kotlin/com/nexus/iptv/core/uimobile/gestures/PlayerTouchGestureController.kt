package com.nexus.iptv.core.uimobile.gestures

import kotlin.math.abs

object PlayerTouchGestureController {
    enum class TouchZone { LEFT_BRIGHTNESS, RIGHT_VOLUME, CENTER_SEEK }

    fun detectZone(touchX: Float, screenWidth: Float): TouchZone {
        if (screenWidth <= 0f) return TouchZone.CENTER_SEEK
        val ratio = touchX / screenWidth
        return when {
            ratio < 0.35f -> TouchZone.LEFT_BRIGHTNESS
            ratio > 0.65f -> TouchZone.RIGHT_VOLUME
            else -> TouchZone.CENTER_SEEK
        }
    }

    fun computeVerticalLevel(
        currentLevel: Float,
        deltaY: Float,
        viewportHeight: Float,
        minLevel: Float = 0.0f,
        maxLevel: Float = 1.0f
    ): Float {
        if (viewportHeight <= 0f || abs(deltaY) < 1f) return currentLevel
        val step = (-deltaY / (viewportHeight * 0.75f))
        return (currentLevel + step).coerceIn(minLevel, maxLevel)
    }
}
