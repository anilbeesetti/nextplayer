package dev.anilbeesetti.nextplayer.feature.player.state

import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34, 35])
class PictureInPictureStateTest {
    private lateinit var controller: ActivityController<PipActivity>
    private lateinit var activity: PipActivity
    private lateinit var player: TestPlayer
    private lateinit var state: PictureInPictureState
    private lateinit var listeners: DisposableEffectResult

    @Before
    fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication().packageManager)
            .setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        controller = Robolectric.buildActivity(PipActivity::class.java).setup()
        activity = controller.get()
        shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        player = TestPlayer()
        state = PictureInPictureState(player, activity)
        listeners = state.handleListeners(DisposableEffectScope())
    }

    @After
    fun tearDown() {
        listeners.dispose()
        player.release()
        controller.pause().stop().destroy()
    }

    @Test
    fun enteredCallbackUpdatesPipStateWithoutAnAnimationCallback() {
        enterPip()
        assertTrue(state.isInPictureInPictureMode)
    }

    @Test
    fun pipPauseAndPlayActionsControlPlaybackWithoutReturningToFullscreen() = withObservedState {
        enterPip()
        sendAction("pause")
        assertFalse(player.playWhenReady)
        assertFalse(player.isPlaying)
        assertEquals("play", activity.params!!.actions[1].title)

        sendAction("play")
        assertTrue(player.playWhenReady)
        assertTrue(player.isPlaying)
        assertEquals("pause", activity.params!!.actions[1].title)
    }

    @Test
    fun pipSkipActionsControlThePlaylist() = withObservedState {
        enterPip()
        sendAction("skip to next")
        assertEquals(2, player.currentMediaItemIndex)
        sendAction("skip to previous")
        assertEquals(1, player.currentMediaItemIndex)
    }

    @Test
    fun exitingPipRemovesTheReceiverAndReentryRestoresControls() = withObservedState {
        enterPip()
        assertEquals(1, pipReceiverCount())
        exitPip()
        assertFalse(state.isInPictureInPictureMode)
        assertEquals(0, pipReceiverCount())
        sendAction("pause")
        assertTrue(player.isPlaying)

        enterPip()
        sendAction("pause")
        assertFalse(player.isPlaying)
    }

    @Test
    fun disposalRemovesThePipReceiver() = withObservedState {
        enterPip()
        assertEquals(1, pipReceiverCount())
        listeners.dispose()
        assertEquals(0, pipReceiverCount())
        sendAction("pause")
        assertTrue(player.isPlaying)
    }

    @Test
    @Config(sdk = [35])
    fun animationStartThenEnteredRegistersTheReceiverOnlyOnce() = withObservedState {
        activity.onPictureInPictureUiStateChanged(
            // The system constructs this state using a non-public constructor.
            ReflectionHelpers.callConstructor(
                PictureInPictureUiState::class.java,
                ClassParameter.from(Boolean::class.javaPrimitiveType, false),
                ClassParameter.from(Boolean::class.javaPrimitiveType, true),
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(state.isInPictureInPictureMode)
        assertEquals(1, pipReceiverCount())

        enterPip()
        assertEquals(1, pipReceiverCount())
        sendAction("pause")
        assertFalse(player.isPlaying)
        exitPip()
        assertEquals(0, pipReceiverCount())
    }

    private fun withObservedState(block: suspend TestScope.() -> Unit) = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.observe() }
        block()
    }

    private fun enterPip() {
        activity.onPictureInPictureModeChanged(true, Configuration())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun exitPip() {
        activity.onPictureInPictureModeChanged(false, Configuration())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun TestScope.sendAction(title: String) {
        activity.params!!.actions.single { it.title == title }.actionIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    private fun pipReceiverCount(): Int = shadowOf(activity.application).registeredReceivers
        .count { it.intentFilter.hasAction("pip_action") }

    class PipActivity : ComponentActivity() {
        var params: PictureInPictureParams? = null

        override fun setPictureInPictureParams(params: PictureInPictureParams) {
            this.params = params
        }
    }

    private class TestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder().addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                ).build(),
            )
            .setPlaylist((0..2).map { MediaItemData.Builder(it).setDurationUs(60_000_000).build() })
            .setCurrentMediaItemIndex(1)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

        override fun getState(): State = state

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            state = state.buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            state = state.buildUpon().setCurrentMediaItemIndex(mediaItemIndex).build()
            return Futures.immediateVoidFuture()
        }
    }
}
