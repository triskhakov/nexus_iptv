package com.nexus.iptv.core.uitv.focus

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class RemoteAction {
    SELECT_OK, LONG_PRESS_CONTEXT_MENU, OPEN_SIDE_MENU, NAVIGATE_BACK,
    CHANNEL_UP, CHANNEL_DOWN, TOGGLE_FAVORITE, SHOW_EPG_INFO
}

object TvRemoteKeyMapper {
    fun mapKeyEvent(keyCode: Int, action: Int, repeatCount: Int): RemoteAction? {
        if (action != KeyEvent.ACTION_DOWN) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (repeatCount > 0) RemoteAction.LONG_PRESS_CONTEXT_MENU else RemoteAction.SELECT_OK
            KeyEvent.KEYCODE_MENU -> RemoteAction.OPEN_SIDE_MENU
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> RemoteAction.NAVIGATE_BACK
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> RemoteAction.CHANNEL_UP
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> RemoteAction.CHANNEL_DOWN
            KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_PROG_YELLOW -> RemoteAction.TOGGLE_FAVORITE
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_PROG_BLUE -> RemoteAction.SHOW_EPG_INFO
            else -> null
        }
    }
}

fun Modifier.nexusTvFocusable(
    accentColor: Color,
    cornerRadius: Dp = 14.dp,
    focusedScale: Float = 1.04f,
    onSelect: () -> Unit,
    onContextMenu: (() -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1.0f,
        animationSpec = tween(durationMillis = 140),
        label = "tv_focus_scale"
    )

    this
        .scale(scale)
        .onFocusChanged { focusState -> isFocused = focusState.isFocused }
        .border(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) accentColor else Color.White.copy(alpha = 0.10f),
            shape = RoundedCornerShape(cornerRadius)
        )
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            when (TvRemoteKeyMapper.mapKeyEvent(native.keyCode, native.action, native.repeatCount)) {
                RemoteAction.SELECT_OK -> { onSelect(); true }
                RemoteAction.LONG_PRESS_CONTEXT_MENU, RemoteAction.OPEN_SIDE_MENU -> {
                    if (onContextMenu != null) { onContextMenu(); true } else false
                }
                else -> false
            }
        }
        .focusable()
}
