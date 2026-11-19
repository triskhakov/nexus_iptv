package com.nexus.iptv.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.nexus.iptv.core.model.DeviceFormFactor

object DeviceFormFactorDetector {
    fun detect(context: Context): DeviceFormFactor {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val pm = context.packageManager
        val hasLeanbackFeature = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTouchScreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return detectFromCapabilities(
            isTelevisionUiMode = isTelevisionMode,
            hasLeanback = hasLeanbackFeature,
            hasTouchScreen = hasTouchScreen,
            smallestScreenWidthDp = context.resources.configuration.smallestScreenWidthDp,
            orientation = context.resources.configuration.orientation
        )
    }

    fun detectFromCapabilities(
        isTelevisionUiMode: Boolean,
        hasLeanback: Boolean,
        hasTouchScreen: Boolean,
        smallestScreenWidthDp: Int,
        orientation: Int
    ): DeviceFormFactor {
        if (isTelevisionUiMode || hasLeanback || !hasTouchScreen) return DeviceFormFactor.TV_DPAD
        if (smallestScreenWidthDp >= 600 || orientation == Configuration.ORIENTATION_LANDSCAPE) return DeviceFormFactor.TABLET_OR_LANDSCAPE
        return DeviceFormFactor.MOBILE_PORTRAIT
    }
}
