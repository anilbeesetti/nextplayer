package dev.anilbeesetti.nextplayer.feature.player.utils

import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer

class VolumeManager(private val audioManager: AudioManager) {

    var loudnessEnhancer: LoudnessEnhancer? = null
        set(value) {
            if (currentVolume > maxStreamVolume) {
                try {
                    value?.enabled = true
                    value?.setTargetGain(currentLoudnessGain.toInt())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            field = value
        }
    val currentStreamVolume get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val maxStreamVolume get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    var currentVolume = currentStreamVolume.toFloat()
        private set
    val maxVolume get() = maxStreamVolume.times(loudnessEnhancer?.let { 2 } ?: 1)

    val currentLoudnessGain get() = (currentVolume - maxStreamVolume) * (MAX_VOLUME_BOOST / maxStreamVolume)
    val volumePercentage get() = (currentVolume / maxStreamVolume.toFloat()).times(100).toInt()

    @Suppress("DEPRECATION")
    fun setVolume(volume: Float, showVolumePanel: Boolean = false) {
        currentVolume = volume.coerceIn(0f, maxVolume.toFloat())

        if (currentVolume <= maxStreamVolume) {
            loudnessEnhancer?.enabled = false
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                currentVolume.toInt(),
                if (showVolumePanel && audioManager.isWiredHeadsetOn) AudioManager.FLAG_SHOW_UI else 0,
            )
        } else {
            try {
                loudnessEnhancer?.enabled = true
                loudnessEnhancer?.setTargetGain(currentLoudnessGain.toInt())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun increaseVolume(showVolumePanel: Boolean = false) {
        setVolume(currentVolume + 1, showVolumePanel)
    }

    fun decreaseVolume(showVolumePanel: Boolean = false) {
        setVolume(currentVolume - 1, showVolumePanel)
    }

    companion object {
        const val MAX_VOLUME_BOOST = 2000
    }
}
