package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.feature.player.extensions.isPortrait

@UnstableApi
@Composable
fun rememberRotationState(
    player: Player,
    screenOrientation: ScreenOrientation,
): RotationState {
    val activity = LocalActivity.current as ComponentActivity
    val rotationState = remember {
        RotationState(
            activity = activity,
            player = player,
            screenOrientation = screenOrientation,
        )
    }
    DisposableEffect(activity) {
        rotationState.handleListeners(this)
    }
    LaunchedEffect(player) { rotationState.observe() }
    return rotationState
}

@Stable
class RotationState(
    private val activity: ComponentActivity,
    private val player: Player,
    private val screenOrientation: ScreenOrientation,
) {
    var currentRequestedOrientation: Int by mutableIntStateOf(activity.requestedOrientation)
        private set

    init {
        setOrientation()
    }

    fun rotate() {
        activity.requestedOrientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult = with(disposableEffectScope) {
        val configurationChangedListener: Consumer<Configuration> = Consumer {
            currentRequestedOrientation = activity.requestedOrientation
        }

        activity.addOnConfigurationChangedListener(configurationChangedListener)

        onDispose {
            activity.removeOnConfigurationChangedListener(configurationChangedListener)
        }
    }

    suspend fun observe() {
        player.listen { events ->
            if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                if (screenOrientation == ScreenOrientation.VIDEO_ORIENTATION) {
                    val videoOrientation = when {
                        player.videoSize.width == 0 || player.videoSize.height == 0 -> null
                        player.videoSize.isPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    activity.requestedOrientation = videoOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    private fun setOrientation() {
        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.requestedOrientation = when (screenOrientation) {
                ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                ScreenOrientation.VIDEO_ORIENTATION -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}
