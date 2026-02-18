package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.getSystemService
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.service.getIsLoudnessGainSupported
import dev.anilbeesetti.nextplayer.feature.player.service.getLoudnessGain
import dev.anilbeesetti.nextplayer.feature.player.service.setLoudnessGain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun rememberVolumeState(
    player: Player?,
    showVolumePanelIfHeadsetIsOn: Boolean,
): VolumeState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val volumeState = rememberSaveable(
        player,
        saver = Saver<VolumeState, Map<String, Any>>(
            save = {
                mapOf("initialVolume" to it.currentVolume)
            },
            restore = {
                VolumeState(
                    player = player,
                    context = context,
                    showVolumePanelIfHeadsetIsOn = showVolumePanelIfHeadsetIsOn,
                    initialVolume = it["initialVolume"] as Int,
                    scope = scope,
                )
            },
        ),
    ) {
        VolumeState(
            player = player,
            context = context,
            showVolumePanelIfHeadsetIsOn = showVolumePanelIfHeadsetIsOn,
            scope = scope,
        )
    }
    LaunchedEffect(player) { volumeState.initialize() }
    DisposableEffect(context) { volumeState.handleLifecycle(this) }
    return volumeState
}

@Stable
class VolumeState(
    private val player: Player?,
    private val context: Context,
    private val showVolumePanelIfHeadsetIsOn: Boolean,
    private val initialVolume: Int? = null,
    private val scope: CoroutineScope,
) {
    private val audioManager = getSystemService(context, AudioManager::class.java)!!

    private val systemMaxVolume: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private var isLoudnessGainSupported: Boolean by mutableStateOf(false)

    val maxVolume: Int
        get() = if (isLoudnessGainSupported) systemMaxVolume * 2 else systemMaxVolume

    val maxVolumePercentage: Int
        get() = if (isLoudnessGainSupported) MAX_VOLUME_PERCENTAGE_BOOST else MAX_VOLUME_PERCENTAGE_NORMAL

    var currentVolume: Int by mutableIntStateOf(initialVolume ?: audioManager.currentStreamVolume)
        private set

    var volumePercentage: Int by mutableIntStateOf(calculateVolumePercentage())
        private set

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
            }
        }

    suspend fun initialize() {
        val player = player as? MediaController ?: return
        isLoudnessGainSupported = player.getIsLoudnessGainSupported()
        val loudnessGain = player.getLoudnessGain()

        // Sync currentVolume from service's boost state when returning from background
        if (loudnessGain > 0 && isLoudnessGainSupported) {
            val boostVolume = (loudnessGain * systemMaxVolume) / MAX_BOOST_GAIN_MB
            currentVolume = systemMaxVolume + boostVolume
            volumePercentage = calculateVolumePercentage()
        }

        player.listen { events ->
            if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
                scope.launch {
                    isLoudnessGainSupported = player.getIsLoudnessGainSupported()
                }
            }
        }
    }

    private fun setVolume(volume: Int, showVolumePanel: Boolean = false) {
        val clampedVolume = volume.coerceIn(0, maxVolume)
        currentVolume = clampedVolume
        volumePercentage = calculateVolumePercentage()

        if (clampedVolume <= systemMaxVolume) {
            setSystemVolume(clampedVolume, showVolumePanel)
            applyVolumeBoost(0)
        } else {
            setSystemVolume(systemMaxVolume, showVolumePanel)
            applyVolumeBoost(clampedVolume - systemMaxVolume)
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
        val player = player as? MediaController ?: return
        val gainMillibels = (volume.toFloat() / systemMaxVolume * MAX_BOOST_GAIN_MB).toInt()

        try {
            player.setLoudnessGain(gainMillibels)
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
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && device.type == AudioDeviceInfo.TYPE_USB_HEADSET)
        }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val MAX_VOLUME_PERCENTAGE_NORMAL = 100
        private const val MAX_VOLUME_PERCENTAGE_BOOST = 200
        private const val MAX_BOOST_GAIN_MB = 2000
    }
}
