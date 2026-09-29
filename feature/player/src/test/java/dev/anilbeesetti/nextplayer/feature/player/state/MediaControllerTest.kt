package dev.anilbeesetti.nextplayer.feature.player.state

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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
        val session = composeRule.runOnIdle { MediaSession.Builder(composeRule.activity, player).build() }
        val service = composeRule.runOnIdle { bindSessionService(session) }
        val owner = TestLifecycleOwner()
        val visible = mutableStateOf(true)
        var controller: MediaController? = null
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) {
                        val connected = rememberMediaController(onBeforeRelease = { controller?.pause() })
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
            val first = composeRule.runOnIdle { requireNotNull(controller).also { it.play() } }
            composeRule.runOnIdle {
                assertTrue(player.playWhenReady)
                owner.registry.currentState = Lifecycle.State.CREATED
            }
            composeRule.runOnIdle {
                assertNull(controller)
                assertFalse(first.isConnected)
                assertFalse(player.playWhenReady)
                owner.registry.currentState = Lifecycle.State.STARTED
            }
            composeRule.waitForIdle()
            composeRule.waitUntil { controller != null }
            val second = composeRule.runOnIdle { requireNotNull(controller) }
            composeRule.runOnIdle {
                assertNotSame(first, second)
                assertTrue(second.isConnected)
                visible.value = false
            }
            composeRule.runOnIdle {
                assertNull(controller)
                assertFalse(second.isConnected)
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
    fun connectionCompletingAfterStopIsReleasedWithoutBeingPublished() {
        val player = composeRule.runOnIdle { ExoPlayer.Builder(composeRule.activity).build() }
        val session = composeRule.runOnIdle { MediaSession.Builder(composeRule.activity, player).build() }
        val application = shadowOf(composeRule.activity.application)
        val service = composeRule.runOnIdle { bindSessionService(session) }
        val owner = TestLifecycleOwner()
        var controllerWasPublished = false
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
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
                assertFalse(application.boundServiceConnections.contains(connection))
                assertTrue(application.unboundServiceConnections.contains(connection))
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

    private fun bindSessionService(session: MediaSession): ServiceController<TestMediaSessionService> {
        val service = Robolectric.buildService(TestMediaSessionService::class.java).create()
        service.get().session = session
        val application = composeRule.activity.applicationContext as Application
        shadowOf(application).setComponentNameAndServiceForBindService(
            ComponentName(application, PlayerService::class.java),
            service.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
        return service
    }

    class TestMediaSessionService : MediaSessionService() {
        lateinit var session: MediaSession

        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle = registry
    }
}
