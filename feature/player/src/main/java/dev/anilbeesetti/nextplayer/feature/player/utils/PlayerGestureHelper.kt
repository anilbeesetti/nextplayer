package dev.anilbeesetti.nextplayer.feature.player.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Resources
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel
import dev.anilbeesetti.nextplayer.feature.player.R
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekBack
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekForward
import dev.anilbeesetti.nextplayer.feature.player.extensions.shouldFastSeek
import dev.anilbeesetti.nextplayer.feature.player.extensions.swipeToShowStatusBars
import dev.anilbeesetti.nextplayer.feature.player.extensions.togglePlayPause
import kotlin.math.abs
import kotlin.math.roundToInt
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
    private val prefs: PlayerPreferences
        get() = viewModel.playerPrefs.value

    private val shouldFastSeek: Boolean
        get() = playerView.player?.duration?.let { prefs.shouldFastSeek(it) } == true

    private var exoContentFrameLayout: AspectRatioFrameLayout = playerView.findViewById(R.id.exo_content_frame)

    private var volumeTrackerValue = -1f
    private var brightnessTrackerValue = -1f
    private var seeking = false
    private var seekStart = 0L
    private var position = 0L
    private var seekChange = 0L
    private var isPlayingOnSeekStart: Boolean = false

    private var gestureVolumeOpen = false
    private var gestureBrightnessOpen = false

    private var hideVolumeGestureJob: Job? = null
    private var hideBrightnessGestureJob: Job? = null

    private val tapGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                with(playerView) {
                    if (!isControllerFullyVisible) showController() else hideController()
                }
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (activity.isControlsLocked) return false

                playerView.player?.run {
                    when (prefs.doubleTapGesture) {
                        DoubleTapGesture.FAST_FORWARD_AND_REWIND -> {
                            val viewCenterX = playerView.measuredWidth / 2

                            if (event.x.toInt() < viewCenterX) {
                                val newPosition = currentPosition - prefs.seekIncrement.toMillis
                                seekBack(newPosition.coerceAtLeast(0), shouldFastSeek)
                            } else {
                                val newPosition = currentPosition + prefs.seekIncrement.toMillis
                                seekForward(newPosition.coerceAtMost(duration), shouldFastSeek)
                            }
                        }

                        DoubleTapGesture.BOTH -> {
                            val eventPositionX = event.x / playerView.measuredWidth

                            if (eventPositionX < 0.35) {
                                val newPosition = currentPosition - prefs.seekIncrement.toMillis
                                seekBack(newPosition.coerceAtLeast(0), shouldFastSeek)
                            } else if (eventPositionX > 0.65) {
                                val newPosition = currentPosition + prefs.seekIncrement.toMillis
                                seekForward(newPosition.coerceAtMost(duration), shouldFastSeek)
                            } else {
                                playerView.togglePlayPause()
                            }
                        }

                        DoubleTapGesture.PLAY_PAUSE -> playerView.togglePlayPause()

                        DoubleTapGesture.NONE -> return false
                    }
                } ?: return false
                return true
            }
        }
    )

    private val seekGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                firstEvent: MotionEvent?,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (firstEvent == null) return false
                if (inExclusionArea(firstEvent)) return false
                if (activity.isControlsLocked) return false

                if (gestureVolumeOpen || gestureBrightnessOpen) return false
                if (abs(distanceX / distanceY) < 2) return false

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

                val distanceDiff = abs(Utils.pxToDp(distanceX) / 4).coerceIn(0.5f, 10f)
                val change = (distanceDiff * SEEK_STEP_MS).toLong()

                if (distanceX < 0L) {
                    playerView.player?.run {
                        seekChange = (seekChange + change)
                            .takeIf { it + seekStart < duration } ?: (duration - seekStart)
                        position = (seekStart + seekChange).coerceAtMost(duration)
                        seekForward(positionMs = position, shouldFastSeek = shouldFastSeek)
                    }
                } else {
                    playerView.player?.run {
                        seekChange = (seekChange - change)
                            .takeIf { it + seekStart > 0 } ?: (0 - seekStart)
                        position = seekStart + seekChange
                        seekBack(positionMs = position, shouldFastSeek = shouldFastSeek)
                    }
                }
                with(activity.binding) {
                    progressScrubberLayout.visibility = View.VISIBLE
                    seekProgressText.text = Utils.formatDurationMillisSign(seekChange)
                }
                return true
            }
        }
    )

    private val volumeAndBrightnessGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                firstEvent: MotionEvent?,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (firstEvent == null) return false
                if (inExclusionArea(firstEvent)) return false
                if (activity.isControlsLocked) return false

                if (seeking) return false
                if (abs(distanceY / distanceX) < 2) return false

                val viewCenterX = playerView.measuredWidth / 2
                val distanceFull = playerView.measuredHeight * FULL_SWIPE_RANGE_SCREEN_RATIO
                val ratioChange = distanceY / distanceFull

                if (firstEvent.x.toInt() > viewCenterX) {
                    hideVolumeGestureJob?.cancel()

                    val currentStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = maxStreamVolume.times(activity.loudnessEnhancer?.let { 2 } ?: 1)

                    if (volumeTrackerValue == -1f) {
                        volumeTrackerValue = currentStreamVolume.toFloat()
                    }

                    val change = ratioChange * maxStreamVolume
                    volumeTrackerValue = (volumeTrackerValue + change).coerceIn(0f, maxVolume.toFloat())

                    if (volumeTrackerValue <= maxStreamVolume) {
                        activity.loudnessEnhancer?.enabled = false
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeTrackerValue.toInt(), 0)
                    } else {
                        activity.loudnessEnhancer?.enabled = true
                        val loudness = (volumeTrackerValue - maxStreamVolume) * (MAX_VOLUME_BOOST / maxStreamVolume)
                        activity.loudnessEnhancer?.setTargetGain(loudness.toInt())
                    }

                    val volumePercentage = (volumeTrackerValue / maxStreamVolume.toFloat()).times(100).toInt()
                    with(activity.binding) {
                        volumeGestureLayout.visibility = View.VISIBLE
                        volumeProgressBar.max = maxVolume.times(100)
                        volumeProgressBar.progress = volumeTrackerValue.times(100).toInt()
                        volumeProgressText.text = volumePercentage.toString()
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
                    brightnessTrackerValue = (brightnessTrackerValue + change).coerceIn(0f, maxBrightness)

                    val layoutParams = activity.window.attributes
                    layoutParams.screenBrightness = brightnessTrackerValue
                    activity.window.attributes = layoutParams

                    // fixes a bug which makes the action bar reappear after changing the brightness
                    activity.swipeToShowStatusBars()

                    val brightnessPercentage = (brightnessTrackerValue / maxBrightness).times(100).toInt()
                    with(activity.binding) {
                        brightnessGestureLayout.visibility = View.VISIBLE
                        brightnessProgressBar.max = maxBrightness.times(100).toInt()
                        brightnessProgressBar.progress = brightnessTrackerValue.times(100).toInt()
                        brightnessProgressText.text = brightnessPercentage.toString()
                        gestureBrightnessOpen = true
                    }
                }
                return true
            }
        }
    )

    private val zoomGestureDetector = ScaleGestureDetector(
        playerView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private val SCALE_RANGE = 0.25f..4.0f

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (activity.isControlsLocked) return false

                activity.currentVideoSize?.let { videoSize ->
                    val scaleFactor = (exoContentFrameLayout.scaleX * detector.scaleFactor)
                    val updatedVideoScale = (exoContentFrameLayout.width * scaleFactor) / videoSize.width.toFloat()
                    if (updatedVideoScale in SCALE_RANGE) {
                        exoContentFrameLayout.scaleX = scaleFactor
                        exoContentFrameLayout.scaleY = scaleFactor
                    }
                    val currentVideoScale = (exoContentFrameLayout.width * exoContentFrameLayout.scaleX) / videoSize.width.toFloat()
                    with(activity.binding) {
                        progressScrubberLayout.visibility = View.VISIBLE
                        "${(currentVideoScale * 100).roundToInt()}%".also { seekProgressText.text = it }
                    }
                }
                return true
            }
        }
    )

    private fun releaseAction(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP) {
            // hide the volume indicator
            activity.binding.volumeGestureLayout.apply {
                if (visibility == View.VISIBLE) {
                    hideVolumeGestureJob = activity.lifecycleScope.launch {
                        delay(HIDE_DELAY_MILLIS)
                        visibility = View.GONE
                    }
                    gestureVolumeOpen = false
                }
            }
            // hide the brightness indicator
            activity.binding.brightnessGestureLayout.apply {
                if (visibility == View.VISIBLE) {
                    hideBrightnessGestureJob = activity.lifecycleScope.launch {
                        delay(HIDE_DELAY_MILLIS)
                        visibility = View.GONE
                    }
                    if (prefs.rememberPlayerBrightness) {
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
            val insets = playerView.rootWindowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())

            if ((firstEvent.x < insets.left) || (firstEvent.x > (screenWidth - insets.right)) ||
                (firstEvent.y < insets.top) || (firstEvent.y > (screenHeight - insets.bottom))
            ) {
                return true
            }
        } else if (firstEvent.y < playerView.resources.pxToDp(GESTURE_EXCLUSION_AREA_VERTICAL) ||
            firstEvent.y > screenHeight - playerView.resources.pxToDp(GESTURE_EXCLUSION_AREA_VERTICAL) ||
            firstEvent.x < playerView.resources.pxToDp(GESTURE_EXCLUSION_AREA_HORIZONTAL) ||
            firstEvent.x > screenWidth - playerView.resources.pxToDp(GESTURE_EXCLUSION_AREA_HORIZONTAL)
        ) {
            return true
        }
        return false
    }

    init {
        if (prefs.rememberPlayerBrightness) {
            activity.window.attributes.screenBrightness = prefs.playerBrightness
        }

        playerView.setOnTouchListener { _, motionEvent ->
            when (motionEvent.pointerCount) {
                1 -> {
                    tapGestureDetector.onTouchEvent(motionEvent)
                    if (prefs.useSwipeControls) {
                        volumeAndBrightnessGestureDetector.onTouchEvent(motionEvent)
                    }
                    if (prefs.useSeekControls && activity.isFileLoaded) {
                        seekGestureDetector.onTouchEvent(motionEvent)
                    }
                }

                2 -> {
                    zoomGestureDetector.onTouchEvent(motionEvent)
                }
            }
            releaseAction(motionEvent)
            true
        }
    }

    fun onStart() {
        val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        if (volumeTrackerValue > maxStreamVolume) {
            activity.loudnessEnhancer?.enabled = true
            val loudness = (volumeTrackerValue - maxStreamVolume) * (MAX_VOLUME_BOOST / maxStreamVolume)
            activity.loudnessEnhancer?.setTargetGain(loudness.toInt())
        }
    }

    companion object {
        const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
        const val GESTURE_EXCLUSION_AREA_VERTICAL = 48
        const val GESTURE_EXCLUSION_AREA_HORIZONTAL = 24
        const val MAX_VOLUME_BOOST = 2000
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

inline val Int.toMillis get() = this * 1000
