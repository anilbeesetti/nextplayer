package dev.anilbeesetti.nextplayer.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun ControlButtonsPosition.name(): String {
    val stringRes = when (this) {
        ControlButtonsPosition.LEFT_BOTTOM_CORNER -> R.string.left_bottom_corner
        ControlButtonsPosition.RIGHT_BOTTOM_CORNER -> R.string.right_bottom_corner
    }

    return stringResource(stringRes)
}
