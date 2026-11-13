package dev.anilbeesetti.nextplayer.feature.player.state

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.MiniPlayerTestPlayer
import dev.anilbeesetti.nextplayer.feature.player.PlaybackOverlay
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaControllerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun controllerReconnectsOnStartAndReleasesOnStopOrCompositionDisposal() {
        val player = composeRule.runOnIdle { ExoPlayer.Builder(composeRule.activity).build() }
        val session = composeRule.runOnIdle { scrubbingSession(player = player) }
        val service = composeRule.runOnIdle { bindSessionService(session = session) }
        val owner = TestLifecycleOwner()
        val visible = mutableStateOf(value = true)
        var controller: MediaController? = null
        try {
            composeRule.setContent {
                CompositionLocalProvider(value = LocalLifecycleOwner provides owner) {
                    if (visible.value) {
                        val connected =
                            rememberMediaController(onBeforeRelease = { controller?.pause() })
                        SideEffect { controller = connected }
                    } else {
                        SideEffect { controller = null }
                    }
                }
            }
            composeRule.runOnIdle {
                assertNull(controller)
                owner.registry.currentState = Lifecycle.State.STARTED
            }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            val first = composeRule.runOnIdle {
                requireNotNull(value = controller).also { it.play() }
            }
            composeRule.runOnIdle { first.setIsScrubbingModeEnabled(enabled = true) }
            composeRule.runOnIdle {
                assertTrue(player.playWhenReady)
                assertTrue(player.isScrubbingModeEnabled)
                owner.registry.currentState = Lifecycle.State.CREATED
            }
            composeRule.runOnIdle {
                assertNull(controller)
                assertFalse(first.isConnected)
                assertFalse(player.playWhenReady)
                assertFalse(player.isScrubbingModeEnabled)
                owner.registry.currentState = Lifecycle.State.STARTED
            }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            val second = composeRule.runOnIdle { requireNotNull(value = controller) }
            composeRule.runOnIdle { second.setIsScrubbingModeEnabled(enabled = true) }
            composeRule.runOnIdle {
                assertNotSame(
                    first,
                    second,
                )
                assertTrue(second.isConnected)
                assertTrue(player.isScrubbingModeEnabled)
                visible.value = false
            }
            composeRule.runOnIdle {
                assertNull(controller)
                assertFalse(second.isConnected)
                assertFalse(player.isScrubbingModeEnabled)
            }
        } finally {
            composeRule.runOnIdle {
                owner.registry.currentState = Lifecycle.State.DESTROYED
                service.destroy()
                session.release()
                player.release()
            }
        }
    }

    @Test
    fun expandedOverlayDoesNotRevealBrowsingDuringBackgroundAndForeground() {
        val player = composeRule.runOnIdle {
            ExoPlayer.Builder(composeRule.activity).build().apply {
                setMediaItem(MediaItem.fromUri("file:///foreground-test.mp4"))
            }
        }
        val session = composeRule.runOnIdle { scrubbingSession(player) }
        val service = composeRule.runOnIdle { bindSessionService(session) }
        val owner = TestLifecycleOwner()
        val expandedReports = mutableListOf<Boolean>()
        val visible = mutableStateOf(true)
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    NextPlayerTheme {
                        if (visible.value) {
                            PlaybackOverlay(
                                compactBottom = 0.dp,
                                compactStart = 0.dp,
                                hidden = false,
                                expansionRequest = 1L,
                                focusRequester = remember { FocusRequester() },
                                onCompactHeightChanged = {},
                                onVisibilityChanged = {},
                                onExpandedChanged = { expandedReports += it },
                            )
                        }
                    }
                }
            }
            composeRule.onNodeWithTag("playback_reconnecting").assertIsDisplayed()
            composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
            composeRule.runOnIdle {
                expandedReports.clear()
                owner.registry.currentState = Lifecycle.State.CREATED
            }
            composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
            composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
            composeRule.runOnIdle {
                assertFalse(
                    "Reconnecting must not expose browsing",
                    expandedReports.contains(false),
                )
                visible.value = false
            }
        } finally {
            composeRule.runOnIdle {
                visible.value = false
                owner.registry.currentState = Lifecycle.State.DESTROYED
            }
            composeRule.runOnIdle {
                service.destroy()
                session.release()
                player.release()
            }
        }
    }

    @Test
    fun retainedControllerReleasesIdlePlaybackAndDisposalWithoutWaitingForForeground() {
        val player = composeRule.runOnIdle { MiniPlayerTestPlayer() }
        val session = composeRule.runOnIdle {
            MediaSession.Builder(
                composeRule.activity,
                player,
            ).build()
        }
        val service = composeRule.runOnIdle { bindSessionService(session) }
        val owner = TestLifecycleOwner()
        val visible = mutableStateOf(true)
        var controller: MediaController? = null
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) {
                        val connected = rememberMediaController(
                            ownsVideoSurface = false,
                            keepConnectedInBackground = true,
                        )
                        SideEffect { controller = connected }
                    } else {
                        SideEffect { controller = null }
                    }
                }
            }
            composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            val first = composeRule.runOnIdle { requireNotNull(controller) }
            composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            composeRule.runOnIdle {
                assertSame(
                    first,
                    controller,
                )
            }
            for (output in listOf(
                PlaybackConnection.CONNECTING,
                PlaybackConnection.CAST,
                PlaybackConnection.LOST,
            )) {
                composeRule.runOnIdle {
                    session.sessionExtras =
                        Bundle().apply { PlaybackPresentation(output).writeTo(this) }
                }
                composeRule.runOnIdle {
                    player.update {
                        setPlaylist(emptyList())
                        setPlaybackState(Player.STATE_IDLE)
                    }
                }
                composeRule.runOnIdle {
                    assertSame(
                        first,
                        controller,
                    )
                }
            }
            composeRule.runOnIdle {
                session.sessionExtras = Bundle().apply {
                    putBoolean(
                        CustomCommands.SESSION_RELEASE_BLOCKED_KEY,
                        true,
                    )
                }
            }
            composeRule.runOnIdle {
                assertSame(
                    first,
                    controller,
                )
            }
            composeRule.runOnIdle { session.sessionExtras = Bundle.EMPTY }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller == null }
            composeRule.runOnIdle { assertFalse(first.isConnected) }

            composeRule.runOnIdle {
                owner.registry.currentState = Lifecycle.State.STARTED
                player.update {
                    setPlaylist(listOf(player.item(0)))
                    setPlaybackState(Player.STATE_READY)
                }
            }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            val second = composeRule.runOnIdle { requireNotNull(controller) }
            composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            composeRule.runOnIdle {
                assertSame(
                    second,
                    controller,
                )
            }
            composeRule.runOnIdle { player.update { setPlaybackState(Player.STATE_ENDED) } }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller == null }
            composeRule.runOnIdle { assertFalse(second.isConnected) }

            composeRule.runOnIdle {
                owner.registry.currentState = Lifecycle.State.STARTED
                player.update { setPlaybackState(Player.STATE_READY) }
            }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            val third = composeRule.runOnIdle { requireNotNull(controller) }
            composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            composeRule.runOnIdle { visible.value = false }
            composeRule.runOnIdle {
                assertNull(controller)
                assertFalse(third.isConnected)
            }
        } finally {
            composeRule.runOnIdle {
                visible.value = false
                owner.registry.currentState = Lifecycle.State.DESTROYED
            }
            composeRule.runOnIdle {
                service.destroy()
                session.release()
                player.release()
            }
        }
    }

    @Test
    fun controllerClearsVideoSurfaceOnStopOrCompositionDisposalWithoutPausingPlayback() =
        checkVideoSurfaceOnRelease(ownsVideoSurface = true)

    @Test
    fun homeControllerLeavesVideoSurfaceAndScrubbingUntouched() =
        checkVideoSurfaceOnRelease(ownsVideoSurface = false)

    private fun checkVideoSurfaceOnRelease(ownsVideoSurface: Boolean) {
        val player = composeRule.runOnIdle { ExoPlayer.Builder(composeRule.activity).build() }
        val session = composeRule.runOnIdle { scrubbingSession(player) }
        val service = composeRule.runOnIdle { bindSessionService(session) }
        val owner = TestLifecycleOwner()
        val visible = mutableStateOf(true)
        val surfaceTexture = SurfaceTexture(0)
        val surface = Surface(surfaceTexture)
        var controller: MediaController? = null
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) {
                        val connected = rememberMediaController(ownsVideoSurface = ownsVideoSurface)
                        SideEffect { controller = connected }
                    } else {
                        SideEffect { controller = null }
                    }
                }
            }
            composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            composeRule.runOnIdle {
                requireNotNull(controller).apply {
                    setVideoSurface(surface)
                    setIsScrubbingModeEnabled(true)
                    play()
                }
            }
            composeRule.runOnIdle {
                assertEquals(
                    Size.UNKNOWN,
                    player.surfaceSize,
                )
                assertTrue(player.playWhenReady)
                owner.registry.currentState = Lifecycle.State.CREATED
            }
            composeRule.runOnIdle {
                assertEquals(
                    if (ownsVideoSurface) Size.ZERO else Size.UNKNOWN,
                    player.surfaceSize,
                )
                assertTrue(player.playWhenReady)
                assertEquals(
                    !ownsVideoSurface,
                    player.isScrubbingModeEnabled,
                )
                owner.registry.currentState = Lifecycle.State.STARTED
            }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            composeRule.runOnIdle { requireNotNull(controller).setVideoSurface(surface) }
            composeRule.runOnIdle {
                assertEquals(
                    Size.UNKNOWN,
                    player.surfaceSize,
                )
                visible.value = false
            }
            composeRule.runOnIdle {
                assertEquals(
                    if (ownsVideoSurface) Size.ZERO else Size.UNKNOWN,
                    player.surfaceSize,
                )
                assertTrue(player.playWhenReady)
            }
        } finally {
            composeRule.runOnIdle {
                owner.registry.currentState = Lifecycle.State.DESTROYED
                service.destroy()
                session.release()
                player.release()
                surface.release()
                surfaceTexture.release()
            }
        }
    }

    private fun scrubbingSession(player: ExoPlayer): MediaSession = MediaSession.Builder(
        composeRule.activity,
        player,
    )
        .setCallback(object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED.sessionCommand)
                            .build(),
                    )
                    .build()

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                player.setIsScrubbingModeEnabled(
                    args.getBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        })
        .build()

    @Test
    fun connectionCompletingAfterStopIsReleasedWithoutBeingPublished() {
        val player = composeRule.runOnIdle { ExoPlayer.Builder(composeRule.activity).build() }
        val session = composeRule.runOnIdle {
            MediaSession.Builder(
                composeRule.activity,
                player,
            ).build()
        }
        val application = shadowOf(composeRule.activity.application)
        val service = composeRule.runOnIdle { bindSessionService(session = session) }
        val owner = TestLifecycleOwner()
        var controllerWasPublished = false
        try {
            composeRule.setContent {
                CompositionLocalProvider(value = LocalLifecycleOwner provides owner) {
                    val controller = rememberMediaController()
                    SideEffect { if (controller != null) controllerWasPublished = true }
                }
            }
            val connection = composeRule.runOnIdle {
                owner.registry.currentState = Lifecycle.State.STARTED
                val connection = application.boundServiceConnections.single()
                owner.registry.currentState = Lifecycle.State.CREATED
                connection
            }
            composeRule.runOnIdle {
                assertFalse(controllerWasPublished)
                assertFalse(application.boundServiceConnections.contains(element = connection))
                assertTrue(application.unboundServiceConnections.contains(element = connection))
            }
        } finally {
            composeRule.runOnIdle {
                owner.registry.currentState = Lifecycle.State.DESTROYED
                service.destroy()
                session.release()
                player.release()
            }
        }
    }

    @Test
    fun refusedHomeConnectionDoesNotCrashOrPublishController() {
        val service = composeRule.runOnIdle {
            Robolectric.buildService(RejectingMediaSessionService::class.java).create()
        }
        val application = composeRule.activity.application
        shadowOf(application).setComponentNameAndServiceForBindService(
            ComponentName(
                application,
                PlayerService::class.java,
            ),
            service.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
        var controller: MediaController? = null
        try {
            composeRule.setContent {
                val connected = rememberMediaController(ownsVideoSurface = false)
                SideEffect { controller = connected }
            }
            composeRule.waitUntil { service.get().attemptedConnection }
            composeRule.runOnIdle { assertNull(controller) }
        } finally {
            composeRule.runOnIdle { service.destroy() }
        }
    }

    class RejectingMediaSessionService : MediaSessionService() {
        var attemptedConnection = false
        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
            attemptedConnection = true
            return null
        }
    }

    private fun bindSessionService(
        session: MediaSession,
    ): ServiceController<TestMediaSessionService> {
        val service = Robolectric.buildService(TestMediaSessionService::class.java).create()
        service.get().session = session
        val application = composeRule.activity.applicationContext as Application
        shadowOf(application).setComponentNameAndServiceForBindService(
            ComponentName(
                application,
                PlayerService::class.java,
            ),
            service.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
        return service
    }

    class TestMediaSessionService : MediaSessionService() {
        lateinit var session: MediaSession

        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
            session
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(owner = this).apply {
            currentState =
                Lifecycle.State.CREATED
        }
        override val lifecycle: Lifecycle = registry
    }
}
