package dev.anilbeesetti.nextplayer.feature.player.utils

import android.view.WindowManager
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.extensions.currentBrightness
import dev.anilbeesetti.nextplayer.feature.player.extensions.swipeToShowStatusBars

class BrightnessManager(private val activity: PlayerActivity) {

    var currentBrightness = activity.currentBrightness
    val maxBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL

    val brightnessPercentage get() = (currentBrightness / maxBrightness).times(100).toInt()

    fun setBrightness(brightness: Float) {
        currentBrightness = brightness.coerceIn(0f, maxBrightness)
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = currentBrightness
        activity.window.attributes = layoutParams

        // fixes a bug which makes the action bar reappear after changing the brightness
        activity.swipeToShowStatusBars()
    }
}
