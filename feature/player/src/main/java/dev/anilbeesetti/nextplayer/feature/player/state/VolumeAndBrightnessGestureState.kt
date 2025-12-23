package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberVolumeAndBrightnessGestureState(
    showVolumePanelIfHeadsetIsOn: Boolean,
): VolumeAndBrightnessGestureState {
    val volumeState = rememberVolumeState(showVolumePanelIfHeadsetIsOn)
    val brightnessState = rememberBrightnessState()
    val coroutineScope = rememberCoroutineScope()
    val volumeAndBrightnessGestureState = remember {
        VolumeAndBrightnessGestureState(
            volumeState = volumeState,
            brightnessState = brightnessState,
            coroutineScope = coroutineScope,
        )
    }
    return volumeAndBrightnessGestureState
}

@Stable
class VolumeAndBrightnessGestureState(
    private val volumeState: VolumeState,
    private val brightnessState: BrightnessState,
    private val coroutineScope: CoroutineScope,
    private val sensitivity: Float = 0.05f,
) {
    var activeGesture: VerticalGesture? by mutableStateOf(null)
        private set

    var volumeChangePercentage: Int by mutableIntStateOf(0)
        private set

    var brightnessChangePercentage: Int by mutableIntStateOf(0)
        private set

    private var startingY = 0f
    private var startVolumePercentage = 0
    private var startBrightnessPercentage = 0
    private var job: Job? = null

    fun onDragStart(offset: Offset, size: IntSize) {
        val viewCenterX = size.width / 2
        job?.cancel()
        activeGesture = when {
            offset.x < viewCenterX -> VerticalGesture.BRIGHTNESS
            else -> VerticalGesture.VOLUME
        }
        startingY = offset.y
        startVolumePercentage = volumeState.volumePercentage
        startBrightnessPercentage = brightnessState.brightnessPercentage
    }

    fun onDrag(change: PointerInputChange, dragAmount: Float) {
        val activeGesture = activeGesture ?: return
        if (change.isConsumed) return

        when (activeGesture) {
            VerticalGesture.VOLUME -> {
                val newVolume = startVolumePercentage + ((startingY - change.position.y) * sensitivity).toInt()
                volumeChangePercentage = (newVolume - startVolumePercentage).coerceIn(
                    minimumValue = 0 - startVolumePercentage,
                    maximumValue = 100 - startVolumePercentage,
                )
                brightnessChangePercentage = 0
                volumeState.updateVolumePercentage(newVolume)
            }
            VerticalGesture.BRIGHTNESS -> {
                val newBrightness = startBrightnessPercentage + ((startingY - change.position.y) * sensitivity).toInt()
                brightnessChangePercentage = (newBrightness - startBrightnessPercentage).coerceIn(
                    minimumValue = 0 - startBrightnessPercentage,
                    maximumValue = 100 - startBrightnessPercentage,
                )
                volumeChangePercentage = 0
                brightnessState.updateBrightnessPercentage(newBrightness)
            }
        }
    }

    fun onDragEnd() {
        startingY = 0f
        startVolumePercentage = 0
        startBrightnessPercentage = 0

        job?.cancel()
        job = coroutineScope.launch {
            delay(1.seconds)
            activeGesture = null
            volumeChangePercentage = 0
            brightnessChangePercentage = 0
        }
    }
}

enum class VerticalGesture {
    VOLUME,
    BRIGHTNESS,
}
