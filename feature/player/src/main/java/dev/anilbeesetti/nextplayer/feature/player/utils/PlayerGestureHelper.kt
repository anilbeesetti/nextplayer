package dev.anilbeesetti.nextplayer.feature.player.utils

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekBack
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekForward
import dev.anilbeesetti.nextplayer.feature.player.extensions.shouldFastSeek
import dev.anilbeesetti.nextplayer.feature.player.extensions.swipeToShowStatusBars
import dev.anilbeesetti.nextplayer.feature.player.extensions.togglePlayPause
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@SuppressLint("ClickableViewAccessibility")
class PlayerGestureHelper(
    private val viewModel: PlayerViewModel,
    private val activity: PlayerActivity,
    private val playerView: PlayerView,
    private val audioManager: AudioManager
) {
    private val playerPreferences: PlayerPreferences
        get() = viewModel.preferences.value

    private val shouldFastSeek: Boolean
        get() = playerView.player?.duration?.let { playerPreferences.shouldFastSeek(it) } == true

    private var volumeTrackerValue = -1f
    private var brightnessTrackerValue = -1f
    private var seeking = false
    private var seekStart = 0L
    private var position = 0L
    private var seekChange = 0L
    private var isPlayingOnSeekStart: Boolean = false
    private var isControllerAutoShow = false

    private var gestureVolumeOpen = false
    private var gestureBrightnessOpen = false

    private var hideVolumeGestureJob: Job? = null
    private var hideBrightnessGestureJob: Job? = null

    private val tapGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                playerView.apply {
                    if (!isControllerFullyVisible) showController() else hideController()
                }
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                // Disables double tap gestures if view is locked
                if (activity.isControlsLocked) return false

                when (playerPreferences.doubleTapGesture) {
                    DoubleTapGesture.PLAY_PAUSE -> {
                        playerView.togglePlayPause()
                    }

                    DoubleTapGesture.FAST_FORWARD_AND_REWIND -> {
                        val viewCenterX = playerView.measuredWidth / 2
                        val currentPos = playerView.player?.currentPosition ?: 0

                        playerView.player?.let { player ->
                            if (event.x.toInt() < viewCenterX) {
                                val newPosition = currentPos - C.DEFAULT_SEEK_BACK_INCREMENT_MS
                                player.seekBack(
                                    positionMs = newPosition.coerceAtLeast(0),
                                    shouldFastSeek = shouldFastSeek
                                )
                            } else {
                                val newPosition = currentPos + C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
                                player.seekForward(
                                    positionMs = newPosition.coerceAtMost(player.duration),
                                    shouldFastSeek = shouldFastSeek
                                )
                            }
                        }
                    }

                    DoubleTapGesture.BOTH -> {
                        val eventPositionPercentageX = event.x / playerView.measuredWidth
                        val currentPos = playerView.player?.currentPosition ?: 0

                        playerView.player?.let { player ->
                            when {
                                eventPositionPercentageX < 0.35 -> {
                                    val newPosition = currentPos - C.DEFAULT_SEEK_BACK_INCREMENT_MS
                                    player.seekBack(
                                        positionMs = newPosition.coerceAtLeast(0),
                                        shouldFastSeek = shouldFastSeek
                                    )
                                }

                                eventPositionPercentageX > 0.65 -> {
                                    val newPosition =
                                        currentPos + C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
                                    player.seekForward(
                                        positionMs = newPosition.coerceAtMost(player.duration),
                                        shouldFastSeek = shouldFastSeek
                                    )
                                }

                                else -> playerView.togglePlayPause()
                            }
                        }
                    }

                    DoubleTapGesture.NONE -> return false
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

                // Disables gesture if view is locked
                if (activity.isControlsLocked) return false

                if (abs(distanceX / distanceY) < 2) return false
                if (gestureVolumeOpen || gestureBrightnessOpen) return false
                playerView.controllerAutoShow = playerView.isControllerFullyVisible

                if (!seeking) {
                    seekChange = 0L
                    seekStart = playerView.player?.currentPosition ?: 0L
                    if (playerView.player?.isPlaying == true) {
                        playerView.player?.pause()
                        isPlayingOnSeekStart = true
                    }
                    seeking = true
                }

                val distanceDiff =
                    0.5f.coerceAtLeast(abs(Utils.pxToDp(distanceX) / 4).coerceAtMost(10.0f))

                val change = distanceDiff * SEEK_STEP_MS
                if (distanceX < 0L) {
                    playerView.player?.let { player ->
                        seekChange = (seekChange + change.toLong()).takeIf {
                            it + seekStart < player.duration
                        } ?: (player.duration - seekStart)
                        position = (seekStart + seekChange).coerceAtMost(player.duration)
                        player.seekForward(positionMs = position, shouldFastSeek = shouldFastSeek)
                    }
                } else {
                    playerView.player?.let { player ->
                        seekChange = (seekChange - change.toLong()).takeIf {
                            it + seekStart > 0
                        } ?: (0 - seekStart)
                        position = seekStart + seekChange
                        player.seekBack(positionMs = position, shouldFastSeek = shouldFastSeek)
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
                if (activity.isControlsLocked) return false

                if (seeking) return false
                if (abs(distanceY / distanceX) < 2) return false

                val viewCenterX = playerView.measuredWidth / 2
                val distanceFull = playerView.measuredHeight * FULL_SWIPE_RANGE_SCREEN_RATIO
                val ratioChange = distanceY / distanceFull

                if (firstEvent.x.toInt() > viewCenterX) {
                    hideVolumeGestureJob?.cancel()

                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                    if (volumeTrackerValue == -1f) {
                        volumeTrackerValue = currentVolume.toFloat()
                    }

                    val change = ratioChange * maxVolume
                    volumeTrackerValue = (volumeTrackerValue + change)
                        .coerceIn(0f, maxVolume.toFloat())

                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        volumeTrackerValue.toInt(),
                        0
                    )

                    val volumePercentage =
                        (volumeTrackerValue / maxVolume.toFloat()).times(100).toInt()

                    with(activity.binding) {
                        gestureVolumeLayout.visibility = View.VISIBLE
                        gestureVolumeProgressBar.max = maxVolume.times(100)
                        gestureVolumeProgressBar.progress = volumeTrackerValue.times(100).toInt()
                        gestureVolumeText.text = volumePercentage.toString()
                        gestureVolumeOpen = true
                    }
                } else {
                    hideBrightnessGestureJob?.cancel()

                    val currentBrightness = activity.currentBrightness
                    val maxBrightness = BRIGHTNESS_OVERRIDE_FULL

                    if (brightnessTrackerValue == -1f) {
                        brightnessTrackerValue = currentBrightness
                    }

                    val change = ratioChange * maxBrightness
                    brightnessTrackerValue = (brightnessTrackerValue + change)
                        .coerceIn(BRIGHTNESS_OVERRIDE_OFF, maxBrightness)

                    val layoutParams = activity.window.attributes
                    layoutParams.screenBrightness = brightnessTrackerValue
                    activity.window.attributes = layoutParams

                    // fixes a bug which makes the action bar reappear after changing the brightness
                    activity.swipeToShowStatusBars()

                    val brightnessPercentage =
                        (brightnessTrackerValue / maxBrightness).times(100).toInt()

                    with(activity.binding) {
                        gestureBrightnessLayout.visibility = View.VISIBLE
                        gestureBrightnessProgressBar.max = maxBrightness.times(100).toInt()
                        gestureBrightnessProgressBar.progress = brightnessTrackerValue.times(100).toInt()
                        gestureBrightnessText.text = brightnessPercentage.toString()
                        gestureBrightnessOpen = true
                    }
                }
                return true
            }
        }
    )

    private fun releaseAction(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP) {
            // hide the volume indicator
            activity.binding.gestureVolumeLayout.apply {
                if (visibility == View.VISIBLE) {
                    hideVolumeGestureJob = activity.lifecycleScope.launch {
                        delay(HIDE_DELAY_MILLIS)
                        visibility = View.GONE
                    }
                    gestureVolumeOpen = false
                }
            }
            // hide the brightness indicator
            activity.binding.gestureBrightnessLayout.apply {
                if (visibility == View.VISIBLE) {
                    hideBrightnessGestureJob = activity.lifecycleScope.launch {
                        delay(HIDE_DELAY_MILLIS)
                        visibility = View.GONE
                    }
                    if (playerPreferences.rememberPlayerBrightness) {
                        viewModel.setPlayerBrightness(activity.window.attributes.screenBrightness)
                    }
                    gestureBrightnessOpen = false
                }
            }

            activity.binding.progressScrubberLayout.apply {
                if (visibility == View.VISIBLE) {
                    visibility = View.GONE
                    if (isPlayingOnSeekStart) playerView.player?.play()
                    playerView.controllerAutoShow = true
                    isPlayingOnSeekStart = false
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
        if (playerPreferences.rememberPlayerBrightness) {
            activity.window.attributes.screenBrightness = playerPreferences.playerBrightness
        }

        playerView.setOnTouchListener { _, motionEvent ->
            when (motionEvent.pointerCount) {
                1 -> {
                    tapGestureDetector.onTouchEvent(motionEvent)
                    if (playerPreferences.useSwipeControls) {
                        volumeAndBrightnessGestureDetector.onTouchEvent(motionEvent)
                    }
                    if (playerPreferences.useSeekControls && activity.isFileLoaded) {
                        seekGestureDetector.onTouchEvent(motionEvent)
                    }
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

fun Resources.pxToDp(px: Int) = (px * displayMetrics.density).toInt()

val Activity.currentBrightness: Float
    get() = when (val brightness = window.attributes.screenBrightness) {
        in BRIGHTNESS_OVERRIDE_OFF..BRIGHTNESS_OVERRIDE_FULL -> brightness
        else -> Settings.System.getFloat(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255
    }

