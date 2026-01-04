package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.getSystemService

@Composable
fun rememberVolumeState(
    showVolumePanelIfHeadsetIsOn: Boolean,
): VolumeState {
    val context = LocalContext.current
    val volumeState = remember { VolumeState(context, showVolumePanelIfHeadsetIsOn) }
    DisposableEffect(context) { volumeState.handleListeners(this) }
    return volumeState
}

class VolumeState(
    private val context: Context,
    private val showVolumePanelIfHeadsetIsOn: Boolean,
) {
    companion object Companion {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }

    private val audioManager = getSystemService(context, AudioManager::class.java)!!
    val maxVolume: Int = audioManager.maxVolume
    var currentVolume: Int by mutableIntStateOf(audioManager.currentVolume)
        private set

    var volumePercentage: Int by mutableIntStateOf(audioManager.volumePercentage)
        private set

    fun updateVolumePercentage(percentage: Int) {
        setVolume(volume = percentage.coerceIn(0, 100) * maxVolume / 100)
    }

    fun increaseVolume(showVolumePanel: Boolean = false) {
        setVolume(currentVolume + 1, showVolumePanel)
    }

    fun decreaseVolume(showVolumePanel: Boolean = false) {
        setVolume(currentVolume - 1, showVolumePanel)
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult = with(disposableEffectScope) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == VOLUME_CHANGED_ACTION) {
                    currentVolume = audioManager.currentVolume
                    volumePercentage = audioManager.volumePercentage
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED_ACTION))

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    private fun setVolume(volume: Int, showVolumePanel: Boolean = false) {
        val shouldShowUiIfHeadset = showVolumePanelIfHeadsetIsOn && audioManager.isHeadsetOn
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            volume,
            if (showVolumePanel || shouldShowUiIfHeadset) AudioManager.FLAG_SHOW_UI else 0,
        )
    }

    private val AudioManager.currentVolume: Int
        get() = getStreamVolume(AudioManager.STREAM_MUSIC)

    private val AudioManager.maxVolume: Int
        get() = getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private val AudioManager.volumePercentage: Int
        get() = (currentVolume.toFloat() / maxVolume.toFloat() * 100).toInt()

    private val AudioManager.isHeadsetOn
        get() = getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
}
