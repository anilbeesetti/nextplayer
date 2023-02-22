package dev.anilbeesetti.nextplayer.feature.player.utils

import android.annotation.SuppressLint
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class PlayerGestureHelper(
    private val activity: PlayerActivity,
    private val playerView: PlayerView,
    private val audioManager: AudioManager
) {

    private var swipeGestureVolumeTrackerValue = -1f
    private var swipeGestureBrightnessTrackerValue = -1f


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

                val distanceFull = playerView.measuredHeight * FULL_SWIPE_RANGE_SCREEN_RATIO
                val ratioChange = distanceY / distanceFull

                if (firstEvent.x.toInt() > viewCenterX) {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (swipeGestureVolumeTrackerValue == -1f) swipeGestureVolumeTrackerValue =
                        currentVolume.toFloat()

                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val change = ratioChange * maxVolume
                    swipeGestureVolumeTrackerValue = (swipeGestureVolumeTrackerValue + change).coerceIn(0f, maxVolume.toFloat())

                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, swipeGestureVolumeTrackerValue.toInt(), 0)

                } else {
                    val brightnessRange = BRIGHTNESS_OVERRIDE_OFF..BRIGHTNESS_OVERRIDE_FULL
                    if (swipeGestureBrightnessTrackerValue == -1f) swipeGestureBrightnessTrackerValue =
                        activity.window.attributes.screenBrightness

                    swipeGestureBrightnessTrackerValue =
                        (swipeGestureBrightnessTrackerValue + ratioChange).coerceIn(brightnessRange)
                    val layoutParams = activity.window.attributes
                    layoutParams.screenBrightness = swipeGestureBrightnessTrackerValue
                    activity.window.attributes = layoutParams
                }
                return true
            }
        }
    )

    init {
        playerView.setOnTouchListener { _, motionEvent ->
            when (motionEvent.pointerCount) {
                1 -> {
                    tapGestureDetector.onTouchEvent(motionEvent)
                    volumeAndBrightnessGestureDetector.onTouchEvent(motionEvent)
                }
                2 -> {
                }
            }
            true
        }
    }


    companion object {
        const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
    }
}