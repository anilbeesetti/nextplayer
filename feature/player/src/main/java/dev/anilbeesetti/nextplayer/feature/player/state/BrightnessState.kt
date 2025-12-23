package dev.anilbeesetti.nextplayer.feature.player.state

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.extensions.brightnessPercentage
import dev.anilbeesetti.nextplayer.feature.player.extensions.currentBrightness

@Composable
fun rememberBrightnessState(): BrightnessState {
    val activity = LocalActivity.current
    val brightnessState = remember { BrightnessState(activity as PlayerActivity) }
    DisposableEffect(activity) { brightnessState.handleListeners(this) }
    return brightnessState
}

@Stable
class BrightnessState(
    private val activity: PlayerActivity,
) {
    val maxBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
    var currentBrightness: Float by mutableFloatStateOf(activity.currentBrightness)
        private set

    var brightnessPercentage: Int by mutableIntStateOf(activity.brightnessPercentage)
        private set

    fun updateBrightnessPercentage(percentage: Int) {
        setBrightness(brightness = percentage.coerceIn(0, 100) * maxBrightness / 100)
    }

    fun setBrightness(brightness: Float) {
        val windowAttributes = activity.window.attributes
        windowAttributes.screenBrightness = brightness.coerceIn(0f, maxBrightness)
        activity.window.attributes = windowAttributes
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult = with(disposableEffectScope) {
        val windowAttributesChangedListener: Consumer<WindowManager.LayoutParams?> = Consumer {
            currentBrightness = activity.currentBrightness
            brightnessPercentage = activity.brightnessPercentage
        }
        activity.addOnWindowAttributesChangedListener(windowAttributesChangedListener)

        onDispose {
            activity.removeOnWindowAttributesChangedListener(windowAttributesChangedListener)
        }
    }
}
