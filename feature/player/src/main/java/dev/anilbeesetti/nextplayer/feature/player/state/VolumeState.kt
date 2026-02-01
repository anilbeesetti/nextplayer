package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.getSystemService
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController

@OptIn(UnstableApi::class)
@Composable
fun rememberVolumeState(
    player: Player?,
    showVolumePanelIfHeadsetIsOn: Boolean,
    volumeBoostEnabled: Boolean = false,
): VolumeState {
    val context = LocalContext.current
    val volumeState = rememberSaveable(
        volumeBoostEnabled,
        saver = Saver<VolumeState, Map<String, Any>>(
            save = {
                mapOf("initialVolume" to it.currentVolume)
            },
            restore = {
                VolumeState(
                    context = context,
                    showVolumePanelIfHeadsetIsOn = showVolumePanelIfHeadsetIsOn,
                    volumeBoostEnabled = volumeBoostEnabled,
                    initialVolume = it["initialVolume"] as Int,
                )
            },
        ),
    ) {
        VolumeState(
            context = context,
            showVolumePanelIfHeadsetIsOn = showVolumePanelIfHeadsetIsOn,
            volumeBoostEnabled = volumeBoostEnabled,
        )
    }
    DisposableEffect(context, volumeBoostEnabled) { volumeState.handleLifecycle(this) }

    LaunchedEffect(player, volumeBoostEnabled) {
        if (volumeBoostEnabled && player is MediaController) {
            val initialAudioSessionId = player.getAudioSessionId()
            if (initialAudioSessionId != 0) {
                volumeState.setAudioSessionId(initialAudioSessionId)
            }

            player.listen { events ->
                if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
                    val newAudioSessionId = player.getAudioSessionId()
                    if (newAudioSessionId != 0) {
                        volumeState.setAudioSessionId(newAudioSessionId)
                    }
                }
            }
        }
    }
    return volumeState
}

@Stable
class VolumeState(
    private val context: Context,
    private val showVolumePanelIfHeadsetIsOn: Boolean,
    private val volumeBoostEnabled: Boolean = false,
    private val initialVolume: Int? = null,
) {
    private val audioManager = getSystemService(context, AudioManager::class.java)!!

    private val systemMaxVolume: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private var loudnessEnhancer: LoudnessEnhancer? = null

    /**
     * Maximum volume level. When volume boost is enabled and LoudnessEnhancer is available,
     * this is double the system max volume (allowing 0-200% range).
     */
    val maxVolume: Int
        get() = if (volumeBoostEnabled && loudnessEnhancer != null) systemMaxVolume * 2 else systemMaxVolume

    /**
     * Maximum volume percentage. 100 for normal, 200 when boost is enabled.
     */
    val maxVolumePercentage: Int
        get() = if (volumeBoostEnabled && loudnessEnhancer != null) MAX_VOLUME_PERCENTAGE_BOOST else MAX_VOLUME_PERCENTAGE_NORMAL

    /**
     * Current volume level (0 to maxVolume).
     * When boost is active, values above systemMaxVolume represent boosted volume.
     */
    var currentVolume: Int by mutableIntStateOf(initialVolume ?: audioManager.currentStreamVolume)
        private set

    /**
     * Current volume as percentage (0-100 normally, 0-200 when boost is enabled).
     */
    var volumePercentage: Int by mutableIntStateOf(calculateVolumePercentage())
        private set

    /**
     * Initializes the LoudnessEnhancer for volume boost.
     * Must be called with a valid audio session ID from the player.
     */
    fun setAudioSessionId(audioSessionId: Int) {
        if (!volumeBoostEnabled || audioSessionId == 0) return

        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)

            if (currentVolume > systemMaxVolume) {
                setVolume(currentVolume)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loudnessEnhancer = null
        }
    }

    /**
     * Updates volume based on percentage (0-100 normally, 0-200 when boost is enabled).
     */
    fun updateVolumePercentage(percentage: Int) {
        val maxPercentage = maxVolumePercentage
        val clampedPercentage = percentage.coerceIn(0, maxPercentage)
        val targetVolume = (clampedPercentage * systemMaxVolume) / MAX_VOLUME_PERCENTAGE_NORMAL

        setVolume(targetVolume)
    }

    fun increaseVolume(showVolumePanel: Boolean = false) {
        setVolume(currentVolume + 1, showVolumePanel)
    }

    fun decreaseVolume(showVolumePanel: Boolean = false) {
        setVolume(currentVolume - 1, showVolumePanel)
    }

    fun handleLifecycle(disposableEffectScope: DisposableEffectScope): DisposableEffectResult =
        with(disposableEffectScope) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == VOLUME_CHANGED_ACTION) {
                        if (currentVolume <= systemMaxVolume) {
                            currentVolume = audioManager.currentStreamVolume
                            volumePercentage = calculateVolumePercentage()
                        }
                    }
                }
            }

            context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED_ACTION))

            onDispose {
                context.unregisterReceiver(receiver)
                loudnessEnhancer?.release()
                loudnessEnhancer = null
            }
        }

    private fun setVolume(volume: Int, showVolumePanel: Boolean = false) {
        val clampedVolume = volume.coerceIn(0, maxVolume)
        currentVolume = clampedVolume
        volumePercentage = calculateVolumePercentage()

        if (clampedVolume <= systemMaxVolume) {
            loudnessEnhancer?.enabled = false
            setSystemVolume(clampedVolume, showVolumePanel)
        } else {
            setSystemVolume(systemMaxVolume, showVolumePanel)
            applyVolumeBoost(clampedVolume)
        }
    }

    private fun setSystemVolume(volume: Int, showVolumePanel: Boolean) {
        val shouldShowUi = showVolumePanel || (showVolumePanelIfHeadsetIsOn && audioManager.isHeadsetOn)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            volume.coerceIn(0, systemMaxVolume),
            if (shouldShowUi) AudioManager.FLAG_SHOW_UI else 0,
        )
    }

    private fun applyVolumeBoost(volume: Int) {
        val enhancer = loudnessEnhancer ?: return

        val boostPortion = volume - systemMaxVolume
        val gainMillibels = (boostPortion.toFloat() / systemMaxVolume * MAX_BOOST_GAIN_MB).toInt()

        try {
            enhancer.setTargetGain(gainMillibels)
            enhancer.enabled = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateVolumePercentage(): Int {
        return (currentVolume.toFloat() / systemMaxVolume * MAX_VOLUME_PERCENTAGE_NORMAL).toInt()
    }

    private val AudioManager.currentStreamVolume: Int
        get() = getStreamVolume(AudioManager.STREAM_MUSIC)

    private val AudioManager.isHeadsetOn: Boolean
        get() = getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val MAX_VOLUME_PERCENTAGE_NORMAL = 100
        private const val MAX_VOLUME_PERCENTAGE_BOOST = 200
        private const val MAX_BOOST_GAIN_MB = 2000
    }
}
