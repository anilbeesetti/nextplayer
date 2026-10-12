package com.graviton.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.graviton.core.model.LongPressGesture
import com.graviton.core.ui.R

@Composable
fun LongPressGesture.name(): String {
    val stringRes = when (this) {
        LongPressGesture.SEEK -> R.string.long_press_action_seek
        LongPressGesture.SPEED_BOOST -> R.string.long_press_action_speed
        LongPressGesture.NONE -> R.string.none
    }

    return stringResource(id = stringRes)
}
