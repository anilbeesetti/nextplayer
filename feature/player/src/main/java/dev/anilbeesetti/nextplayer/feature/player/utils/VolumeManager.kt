package dev.anilbeesetti.nextplayer.feature.player.utils

import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer

class VolumeManager(private val audioManager: AudioManager) {

    var loudnessEnhancer: LoudnessEnhancer? = null
        set(value) {
            field = value
            if (currentVolume > maxStreamVolume) {
                loudnessEnhancer?.enabled = true
                loudnessEnhancer?.setTargetGain(currentLoudnessGain.toInt())
            }
        }
    val currentStreamVolume get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val maxStreamVolume get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    var currentVolume = currentStreamVolume.toFloat()
        private set
    val maxVolume get() = maxStreamVolume.times(loudnessEnhancer?.let { 2 } ?: 1)

    val currentLoudnessGain get() = (currentVolume - maxStreamVolume) * (MAX_VOLUME_BOOST / maxStreamVolume)
    val volumePercentage get() = (currentVolume / maxStreamVolume.toFloat()).times(100).toInt()

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, maxVolume.toFloat())

        if (currentVolume <= maxStreamVolume) {
            loudnessEnhancer?.enabled = false
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume.toInt(), 0)
        } else {
            loudnessEnhancer?.enabled = true
            loudnessEnhancer?.setTargetGain(currentLoudnessGain.toInt())
        }
    }

    fun increaseVolume() {
        setVolume(currentVolume + 1)
    }

    fun decreaseVolume() {
        setVolume(currentVolume - 1)
    }

    companion object {
        const val MAX_VOLUME_BOOST = 2000
    }
}
