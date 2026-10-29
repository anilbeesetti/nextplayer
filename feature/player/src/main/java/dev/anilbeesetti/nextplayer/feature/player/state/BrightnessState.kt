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
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.extensions.brightnessPercentage
import dev.anilbeesetti.nextplayer.feature.player.extensions.currentBrightness

@Composable
fun rememberBrightnessState(): BrightnessState {
    val activity = LocalActivity.current
    val brightnessState = remember { BrightnessState(activity = activity as PlayerActivity) }
    DisposableEffect(key1 = activity) {
        brightnessState.handleListeners(disposableEffectScope = this)
    }
    return brightnessState
}

@Stable
class BrightnessState(private val activity: PlayerActivity) {
    val maxBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
    var currentBrightness: Float by mutableFloatStateOf(activity.currentBrightness)
        private set

    var brightnessPercentage: Int by mutableIntStateOf(activity.brightnessPercentage)
        private set

    private var isInPictureInPictureMode = false
    private var brightnessBeforePictureInPicture =
        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    fun updateBrightnessPercentage(percentage: Int) {
        setBrightness(
            brightness =
                percentage.coerceIn(
                    minimumValue = 0,
                    maximumValue = 100,
                ) * maxBrightness / 100,
        )
    }

    fun setBrightness(brightness: Float) {
        val playerBrightness = brightness.coerceIn(
            minimumValue = 0f,
            maximumValue = maxBrightness,
        )
        if (isInPictureInPictureMode) {
            brightnessBeforePictureInPicture = playerBrightness
            currentBrightness = playerBrightness
            brightnessPercentage = (playerBrightness / maxBrightness * 100).toInt()
        } else {
            setWindowBrightness(brightness = playerBrightness)
        }
    }

    private fun setWindowBrightness(brightness: Float) {
        val windowAttributes = activity.window.attributes
        windowAttributes.screenBrightness = brightness
        activity.window.attributes = windowAttributes
    }

    private fun updatePictureInPictureMode(isInPictureInPictureMode: Boolean) {
        if (this.isInPictureInPictureMode == isInPictureInPictureMode) return
        if (isInPictureInPictureMode) {
            brightnessBeforePictureInPicture = activity.window.attributes.screenBrightness
        }
        this.isInPictureInPictureMode = isInPictureInPictureMode
        // PiP shares the display with other apps, so let the system control its brightness.
        setWindowBrightness(
            brightness = if (isInPictureInPictureMode) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                brightnessBeforePictureInPicture
            },
        )
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult =
        with(receiver = disposableEffectScope) {
            val windowAttributesChangedListener: Consumer<WindowManager.LayoutParams?> = Consumer {
                // System brightness in PiP must not replace the player's remembered brightness.
                if (!isInPictureInPictureMode) {
                    currentBrightness = activity.currentBrightness
                    brightnessPercentage = activity.brightnessPercentage
                }
            }
            val pictureInPictureModeChangedListener = Consumer<PictureInPictureModeChangedInfo> {
                updatePictureInPictureMode(it.isInPictureInPictureMode)
            }
            activity.addOnWindowAttributesChangedListener(
                listener = windowAttributesChangedListener,
            )
            activity.addOnPictureInPictureModeChangedListener(pictureInPictureModeChangedListener)
            updatePictureInPictureMode(activity.isInPictureInPictureMode)

            onDispose {
                activity.removeOnWindowAttributesChangedListener(
                    listener = windowAttributesChangedListener,
                )
                activity.removeOnPictureInPictureModeChangedListener(
                    pictureInPictureModeChangedListener,
                )
            }
        }
}
