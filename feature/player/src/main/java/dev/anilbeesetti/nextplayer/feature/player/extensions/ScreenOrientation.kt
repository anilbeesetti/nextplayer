package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.pm.ActivityInfo
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation

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
