package dev.anilbeesetti.nextplayer.feature.player.utils

import android.annotation.SuppressLint
import android.content.res.Resources
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import kotlin.math.abs

@UnstableApi
@SuppressLint("ClickableViewAccessibility")
class PlayerGestureHelper(
    private val activity: PlayerActivity,
    private val playerView: PlayerView,
    private val audioManager: AudioManager
) {

    private var swipeGestureVolumeTrackerValue = -1f
    private var swipeGestureBrightnessTrackerValue = -1f
    private var seeking = false
    private var seekStart = 0L
    private var position = 0L
    private var seekChange = 0L

    private var swipeGestureVolumeOpen = false
    private var swipeGestureBrightnessOpen = false

    private val tapGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                playerView.apply {
                    if (!isControllerFullyVisible) showController() else hideController()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                playerView.controllerAutoShow = playerView.isControllerFullyVisible
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
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                firstEvent: MotionEvent,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // Excludes area where app gestures conflicting with system gestures
                if (inExclusionArea(firstEvent)) return false

                if (abs(distanceX / distanceY) < 2) return false
                if (swipeGestureVolumeOpen || swipeGestureBrightnessOpen) return false
                playerView.controllerAutoShow = playerView.isControllerFullyVisible

                if (!seeking) {
                    seekChange = 0L
                    seekStart = playerView.player?.currentPosition ?: 0L
                    playerView.player?.pause()
                    seeking = true
                }

                val distanceDiff =
                    0.5f.coerceAtLeast(abs(Utils.pxToDp(distanceX) / 4).coerceAtMost(10.0f))

                val change = distanceDiff * SEEK_STEP_MS
                if (distanceX > 0L) {
                    playerView.player?.let { player ->
                        if (player.duration >= 600000) {
                            player.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                        }
                        seekChange -= change.toLong()
                        position = seekStart + seekChange
                        player.seekTo(position)
                    }
                } else {
                    playerView.player?.let { player ->
                        if (player.duration >= 600000) {
                            player.setSeekParameters(SeekParameters.NEXT_SYNC)
                        }
                        seekChange += change.toLong()
                        position = seekStart + seekChange
                        player.seekTo(position)
                    }
                }
                activity.binding.progressScrubberLayout.visibility = View.VISIBLE
                activity.binding.seekProgressText.text = Utils.formatDurationMillisSign(seekChange)
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
                // Excludes area where app gestures conflicting with system gestures
                if (inExclusionArea(firstEvent)) return false

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
                    playerView.player?.play()
                    playerView.controllerAutoShow = true
                    seeking = false
                }
            }
            seeking = false
        }
    }

    /**
     * Check if [firstEvent] is in the gesture exclusion area
     */
    private fun inExclusionArea(firstEvent: MotionEvent): Boolean {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = playerView.rootWindowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())

            if ((firstEvent.x < insets.left) || (firstEvent.x > (screenWidth - insets.right)) ||
                (firstEvent.y < insets.top) || (firstEvent.y > (screenHeight - insets.bottom))
            ) {
                return true
            }
        } else if (firstEvent.y < playerView.resources.pxToDp(GESTURE_EXCLUSION_AREA_VERTICAL) ||
            firstEvent.y > screenHeight - playerView.resources
                .pxToDp(GESTURE_EXCLUSION_AREA_VERTICAL) ||
            firstEvent.x < playerView.resources.pxToDp(GESTURE_EXCLUSION_AREA_HORIZONTAL) ||
            firstEvent.x > screenWidth - playerView.resources
                .pxToDp(GESTURE_EXCLUSION_AREA_HORIZONTAL)
        ) {
            return true
        }
        return false
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
                    // Do nothing for now
                }
            }
            releaseAction(motionEvent)
            true
        }
    }

    companion object {
        const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
        const val GESTURE_EXCLUSION_AREA_VERTICAL = 48
        const val GESTURE_EXCLUSION_AREA_HORIZONTAL = 24
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

fun Resources.pxToDp(px: Int) = (px * displayMetrics.density).toInt()
