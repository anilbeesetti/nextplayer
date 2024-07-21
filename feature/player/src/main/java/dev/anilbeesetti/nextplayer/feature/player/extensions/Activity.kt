package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.app.Activity
import android.provider.Settings
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Arrays
import timber.log.Timber

/**
* Must call this function after any configuration done to activity to keep system bars behaviour
*/
fun Activity.swipeToShowStatusBars() {
    WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

/**
 * Toggles system bars visibility
 * @param showBars true to show system bars, false to hide
 * @param types [Type.InsetsType] system bars to toggle default is all system bars
 */
fun Activity.toggleSystemBars(showBars: Boolean, @Type.InsetsType types: Int = Type.systemBars()) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showBars) show(types) else hide(types)
    }
}

val Activity.currentBrightness: Float
    get() = when (val brightness = window.attributes.screenBrightness) {
        in WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF..WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL -> brightness
        else -> Settings.System.getFloat(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255
    }

@Suppress("DEPRECATION")
fun Activity.prettyPrintIntent() {
    try {
        Timber.apply {
            d("* action: ${intent.action}")
            d("* data: ${intent.data}")
            d("* type: ${intent.type}")
            d("* package: ${intent.`package`}")
            d("* component: ${intent.component}")
            d("* flags: ${intent.flags}")
            intent.extras?.let { bundle ->
                d("=== Extras ===")
                bundle.keySet().forEachIndexed { i, key ->
                    buildString {
                        append("${i + 1}) $key: ")
                        bundle.get(key).let { append(if (it is Array<*>) Arrays.toString(it) else it) }
                    }.also { d(it) }
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e)
        e.printStackTrace()
    }
}
