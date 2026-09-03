package com.graviton.feature.player.extensions

import android.content.pm.ActivityInfo
import androidx.annotation.StringRes
import com.graviton.core.model.ScreenOrientation
import com.graviton.core.ui.R

@StringRes
fun ScreenOrientation.nameRes(): Int = when (this) {
    ScreenOrientation.AUTOMATIC -> R.string.automatic
    ScreenOrientation.LANDSCAPE -> R.string.landscape
    ScreenOrientation.LANDSCAPE_REVERSE -> R.string.landscape_reverse
    ScreenOrientation.LANDSCAPE_AUTO -> R.string.landscape_auto
    ScreenOrientation.PORTRAIT -> R.string.portrait
    ScreenOrientation.VIDEO_ORIENTATION -> R.string.video_orientation
}

fun ScreenOrientation.toActivityOrientation(videoOrientation: Int? = null): Int {
    return when (this) {
        ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ScreenOrientation.VIDEO_ORIENTATION -> videoOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
