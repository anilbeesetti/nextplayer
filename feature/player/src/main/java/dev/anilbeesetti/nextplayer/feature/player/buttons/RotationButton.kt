package dev.anilbeesetti.nextplayer.feature.player.buttons

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun RotationButton(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current

    PlayerButton(
        modifier = modifier,
        onClick = {
            activity?.requestedOrientation = when (activity.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        },
    ) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_screen_rotation),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
        )
    }
}