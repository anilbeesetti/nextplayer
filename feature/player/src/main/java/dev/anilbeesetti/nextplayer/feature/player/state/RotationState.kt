package dev.anilbeesetti.nextplayer.feature.player.state

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.feature.player.extensions.isPortrait

@Composable
fun rememberRotationState(): RotationState? {
    val activity = LocalActivity.current ?: return null
    return remember(activity) { RotationState(activity) }
}

@Stable
class RotationState(private val activity: Activity) {
    fun rotate() {
        activity.requestedOrientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

/** Applies player-wide orientation policy even when the rotate button is hidden or controls are locked. */
@UnstableApi
@Composable
fun PlayerOrientationEffect(
    player: Player,
    screenOrientation: ScreenOrientation,
) {
    val activity = LocalActivity.current ?: return
    LaunchedEffect(activity, player, screenOrientation) {
        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.requestedOrientation = when (screenOrientation) {
                ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                ScreenOrientation.VIDEO_ORIENTATION -> activity.videoBasedOrientation(player)
            }
        }
        player.listen { events ->
            if (screenOrientation == ScreenOrientation.VIDEO_ORIENTATION && events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                activity.requestedOrientation = activity.videoBasedOrientation(player)
            }
        }
    }
}

@UnstableApi
private fun Activity.videoBasedOrientation(player: Player) = when {
    player.videoSize.width == 0 || player.videoSize.height == 0 -> requestedOrientation
    player.videoSize.isPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}
