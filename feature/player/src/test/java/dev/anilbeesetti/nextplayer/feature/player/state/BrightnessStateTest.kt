package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.res.Configuration
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34, 35])
class BrightnessStateTest {
    private lateinit var activity: PlayerActivity
    private lateinit var state: BrightnessState
    private lateinit var listeners: DisposableEffectResult

    @Before
    fun setUp() {
        // Attach the window without starting the playback service or dependency graph.
        activity = Robolectric.buildActivity(PlayerActivity::class.java).get()
        Settings.System.putFloat(
            activity.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128f,
        )
        state = BrightnessState(activity)
        listeners = state.handleListeners(DisposableEffectScope())
    }

    @After
    fun tearDown() {
        if (::listeners.isInitialized) listeners.dispose()
    }

    @Test
    fun pipFollowsSystemBrightnessAndRestoresPlayerBrightnessOnExit() {
        state.setBrightness(0.8f)

        activity.onPictureInPictureModeChanged(
            true,
            Configuration(),
        )

        assertEquals(
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
            activity.window.attributes.screenBrightness,
        )
        Settings.System.putFloat(
            activity.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            32f,
        )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        assertEquals(
            0.8f,
            state.currentBrightness,
        )
        assertEquals(
            80,
            state.brightnessPercentage,
        )

        activity.onPictureInPictureModeChanged(
            false,
            Configuration(),
        )

        assertEquals(
            0.8f,
            activity.window.attributes.screenBrightness,
        )
        assertEquals(
            0.8f,
            state.currentBrightness,
        )
    }

    @Test
    fun exitingPipWithoutAPlayerOverrideContinuesToFollowSystemBrightness() {
        activity.onPictureInPictureModeChanged(
            true,
            Configuration(),
        )
        Settings.System.putFloat(
            activity.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            32f,
        )

        activity.onPictureInPictureModeChanged(
            false,
            Configuration(),
        )

        assertEquals(
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
            activity.window.attributes.screenBrightness,
        )
        assertEquals(
            32f / 255,
            state.currentBrightness,
        )
    }

    @Test
    fun restoringRememberedBrightnessWhileInPipKeepsSystemControlUntilExit() {
        state.setBrightness(0.8f)
        activity.onPictureInPictureModeChanged(
            true,
            Configuration(),
        )

        state.setBrightness(0.6f)

        assertEquals(
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
            activity.window.attributes.screenBrightness,
        )
        assertEquals(
            0.6f,
            state.currentBrightness,
        )
        assertEquals(
            60,
            state.brightnessPercentage,
        )
        activity.onPictureInPictureModeChanged(
            false,
            Configuration(),
        )
        assertEquals(
            0.6f,
            activity.window.attributes.screenBrightness,
        )
    }

    @Test
    fun duplicateCallbacksAndRepeatedPipEntriesPreserveThePlayerOverride() {
        state.setBrightness(0.8f)
        repeat(2) {
            activity.onPictureInPictureModeChanged(
                true,
                Configuration(),
            )
            activity.onPictureInPictureModeChanged(
                true,
                Configuration(),
            )
            assertEquals(
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                activity.window.attributes.screenBrightness,
            )

            activity.onPictureInPictureModeChanged(
                false,
                Configuration(),
            )
            activity.onPictureInPictureModeChanged(
                false,
                Configuration(),
            )
            assertEquals(
                0.8f,
                activity.window.attributes.screenBrightness,
            )
        }
    }

    @Test
    fun disposingListenersStopsHandlingPipAndWindowChanges() {
        state.setBrightness(0.8f)
        listeners.dispose()

        activity.onPictureInPictureModeChanged(
            true,
            Configuration(),
        )
        assertEquals(
            0.8f,
            activity.window.attributes.screenBrightness,
        )
        state.setBrightness(0.6f)
        assertEquals(
            0.8f,
            state.currentBrightness,
        )
    }
}
