package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize

@Composable
fun rememberVolumeAndBrightnessGestureState(
    showVolumePanelIfHeadsetIsOn: Boolean
): VolumeAndBrightnessGestureState {
    val volumeState = rememberVolumeState(showVolumePanelIfHeadsetIsOn)
    val volumeAndBrightnessGestureState = remember {
        VolumeAndBrightnessGestureState(volumeState)
    }
    LaunchedEffect(volumeState.currentVolume) {
        println("HELLO: Volume ${volumeState.currentVolume}")
    }
    LaunchedEffect(volumeState.volumePercentage) {
        println("HELLO: Percentage ${volumeState.volumePercentage}")
    }
    return volumeAndBrightnessGestureState
}

class VolumeAndBrightnessGestureState(
    private val volumeState: VolumeState,
) {
    var activeGesture: VerticalGesture? by mutableStateOf(null)
        private set

    private var startingY = 0f
    private var startVolumePercentage = 0
    private var startBrightnessPercentage = 0

    fun onDragStart(offset: Offset, size: IntSize) {
        val viewCenterX = size.width / 2
        activeGesture = when {
            offset.x < viewCenterX -> VerticalGesture.BRIGHTNESS
            else -> VerticalGesture.VOLUME
        }
        startingY = offset.y
        startVolumePercentage = volumeState.volumePercentage
    }

    fun onDrag(change: PointerInputChange, dragAmount: Float) {
        val activeGesture = activeGesture ?: return

        when (activeGesture) {
            VerticalGesture.VOLUME -> {
                val newVolume = startVolumePercentage + ((startingY - change.position.y) * 0.03f).toInt()
                volumeState.updateVolumePercentage(newVolume)
            }
            VerticalGesture.BRIGHTNESS -> {
                val newBrightness = startBrightnessPercentage + ((startingY - change.position.y) * 0.03f).toInt()
                // TODO update brightness
            }
        }
    }

    fun onDragEnd() {
        activeGesture = null
        startingY = 0f
        startVolumePercentage = 0
    }
}

enum class VerticalGesture {
    VOLUME, BRIGHTNESS
}