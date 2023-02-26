package dev.anilbeesetti.nextplayer.feature.player.utils

import android.annotation.SuppressLint
import android.content.res.Resources
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class PlayerGestureHelper(
    private val activity: PlayerActivity,
    private val playerView: PlayerView,
    private val audioManager: AudioManager
) {

    private var swipeGestureVolumeTrackerValue = -1f
    private var swipeGestureBrightnessTrackerValue = -1f
    private var seeking = false
    private var seekChange = 0L

    private var swipeGestureVolumeOpen = false
    private var swipeGestureBrightnessOpen = false

    private val tapGestureDetector = GestureDetector(
        playerView.context,
        @UnstableApi object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                playerView.apply {
                    if (!isControllerFullyVisible) showController() else hideController()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (playerView.player?.isPlaying == true) {
                    playerView.player?.pause()
                } else {
                    playerView.player?.play()
                }
                return true
            }
        }
    )


    private val seekGestureDetector = GestureDetector(
        playerView.context,
        @UnstableApi object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                firstEvent: MotionEvent,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {

                if (abs(distanceX / distanceY) < 2) return false
                if (swipeGestureVolumeOpen || swipeGestureBrightnessOpen) return false

                if (!seeking) {
                    seekChange = 0L
                    seeking = true
                }

                val currentPosition = playerView.player?.currentPosition ?: 0L
                val distanceDiff =
                    0.5f.coerceAtLeast(abs(pxToDp(distanceX) / 4).coerceAtMost(10.0f))

                val change = distanceDiff * SEEK_STEP_MS
                if (distanceX > 0L) {
                    playerView.player?.let { player ->
                        if (player.duration >= 600000) {
                            player.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                        }
                        seekChange -= change.toLong()
                        player.seekTo(currentPosition - change.toLong())
                    }
                } else {
                    playerView.player?.let { player ->
                        if (player.duration >= 600000) {
                            player.setSeekParameters(SeekParameters.NEXT_SYNC)
                        }
                        seekChange += change.toLong()
                        player.seekTo(currentPosition + change.toLong())
                    }
                }
                activity.binding.progressScrubberLayout.visibility = View.VISIBLE
                activity.binding.seekProgressText.text = formatMillisSign(seekChange)
                return true
            }
        }
    )

    private val volumeAndBrightnessGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                firstEvent: MotionEvent,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val viewCenterX = playerView.measuredWidth / 2

                if (abs(distanceY / distanceX) < 2) return false
                if (seeking) return false

                val distanceFull = playerView.measuredHeight * FULL_SWIPE_RANGE_SCREEN_RATIO
                val ratioChange = distanceY / distanceFull

                if (firstEvent.x.toInt() > viewCenterX) {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (swipeGestureVolumeTrackerValue == -1f) {
                        swipeGestureVolumeTrackerValue =
                            currentVolume.toFloat()
                    }

                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val change = ratioChange * maxVolume
                    swipeGestureVolumeTrackerValue = (swipeGestureVolumeTrackerValue + change)
                        .coerceIn(
                            minimumValue = 0f,
                            maximumValue = maxVolume.toFloat()
                        )

                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        swipeGestureVolumeTrackerValue.toInt(),
                        0
                    )

                    val volumePercentage = (swipeGestureVolumeTrackerValue / maxVolume.toFloat())
                        .times(100).toInt()
                    val volumeText = "$volumePercentage%"

                    activity.binding.gestureVolumeLayout.visibility = View.VISIBLE
                    activity.binding.gestureVolumeProgressBar.max = maxVolume
                    activity.binding.gestureVolumeProgressBar.progress =
                        swipeGestureVolumeTrackerValue.toInt()
                    activity.binding.gestureVolumeText.text = volumeText

                    swipeGestureVolumeOpen = true
                } else {
                    val brightnessRange = BRIGHTNESS_OVERRIDE_OFF..BRIGHTNESS_OVERRIDE_FULL
                    if (swipeGestureBrightnessTrackerValue == -1f) {
                        val brightness = activity.window.attributes.screenBrightness
                        swipeGestureBrightnessTrackerValue = when (brightness) {
                            in brightnessRange -> brightness
                            else -> Settings.System.getFloat(
                                activity.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS
                            ) / 255
                        }
                    }

                    swipeGestureBrightnessTrackerValue =
                        (swipeGestureBrightnessTrackerValue + ratioChange).coerceIn(brightnessRange)
                    val layoutParams = activity.window.attributes
                    layoutParams.screenBrightness = swipeGestureBrightnessTrackerValue
                    activity.window.attributes = layoutParams

                    // fixes a bug which makes the action bar reappear after changing the brightness
                    activity.swipeToShowStatusBars()

                    val brightnessPercentage =
                        (layoutParams.screenBrightness / BRIGHTNESS_OVERRIDE_FULL)
                            .times(100).toInt()
                    val brightnessText = "$brightnessPercentage%"

                    activity.binding.gestureBrightnessLayout.visibility = View.VISIBLE
                    activity.binding.gestureBrightnessProgressBar.max = BRIGHTNESS_OVERRIDE_FULL
                        .times(100).toInt()
                    activity.binding.gestureBrightnessProgressBar.progress =
                        layoutParams.screenBrightness
                            .times(100).toInt()
                    activity.binding.gestureBrightnessText.text = brightnessText

                    swipeGestureBrightnessOpen = true
                }
                return true
            }
        }
    )

    private val hideVolumeGestureIndicator = Runnable {
        activity.binding.gestureVolumeLayout.visibility = View.GONE
    }

    private val hideBrightnessGestureIndicator = Runnable {
        activity.binding.gestureBrightnessLayout.visibility = View.GONE
    }

    private fun releaseAction(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP) {
            // hide the volume indicator
            activity.binding.gestureVolumeLayout.apply {
                if (visibility == View.VISIBLE) {
                    removeCallbacks(hideVolumeGestureIndicator)
                    postDelayed(hideVolumeGestureIndicator, HIDE_DELAY_MILLIS)
                    swipeGestureVolumeOpen = false
                }
            }
            // hide the brightness indicator
            activity.binding.gestureBrightnessLayout.apply {
                if (visibility == View.VISIBLE) {
                    removeCallbacks(hideBrightnessGestureIndicator)
                    postDelayed(hideBrightnessGestureIndicator, HIDE_DELAY_MILLIS)
                    swipeGestureBrightnessOpen = false
                }
            }

            activity.binding.progressScrubberLayout.apply {
                if (visibility == View.VISIBLE) {
                    visibility = View.GONE
                    seeking = false
                }
            }
            seeking = false
        }
    }

    init {
        playerView.setOnTouchListener { _, motionEvent ->
            when (motionEvent.pointerCount) {
                1 -> {
                    tapGestureDetector.onTouchEvent(motionEvent)
                    volumeAndBrightnessGestureDetector.onTouchEvent(motionEvent)
                    seekGestureDetector.onTouchEvent(motionEvent)
                }
                2 -> {
                }
            }
            releaseAction(motionEvent)
            true
        }
    }

    companion object {
        const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
        const val SEEK_STEP_MS = 1000L
        const val HIDE_DELAY_MILLIS = 1000L
    }
}


@UnstableApi
fun Player.setSeekParameters(seekParameters: SeekParameters) {
    when (this) {
        is ExoPlayer -> this.setSeekParameters(seekParameters)
    }
}

fun pxToDp(px: Float): Float {
    return px / Resources.getSystem().displayMetrics.density
}

fun formatMillis(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatMillisSign(millis: Long): String {
    return if (millis >= 0) {
        "+${formatMillis(millis)}"
    } else {
        "-${formatMillis(abs(millis))}"
    }
}