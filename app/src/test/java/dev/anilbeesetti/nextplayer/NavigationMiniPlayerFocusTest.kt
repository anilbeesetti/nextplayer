package dev.anilbeesetti.nextplayer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabState
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.navigation.rememberTopLevelNavState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    sdk = [36],
    qualifiers = "w960dp-h540dp-television",
)
class NavigationMiniPlayerFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentAndFabCanTraverseThroughActiveMiniPlayer() {
        val context = composeRule.activity
        shadowOf(context.packageManager).setSystemFeature(
            PackageManager.FEATURE_LEANBACK,
            true,
        )
        val player = composeRule.runOnIdle {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(
                    MediaItem.Builder().setMediaId("focus").setUri("file:///focus.mp4").build(),
                )
            }
        }
        val session = composeRule.runOnIdle {
            MediaSession.Builder(
                context,
                player,
            ).build()
        }
        val service = composeRule.runOnIdle {
            Robolectric.buildService(TestSessionService::class.java).create().apply {
                get().session = session
            }
        }
        shadowOf(context.application).setComponentNameAndServiceForBindService(
            ComponentName(
                context,
                PlayerService::class.java,
            ),
            service.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
        try {
            composeRule.setContent {
                NextPlayerTheme {
                    val nav = rememberTopLevelNavState()
                    val fabs = remember {
                        mutableStateMapOf(
                            TopLevelFabKey.MEDIA to TopLevelFabState(
                                NextIcons.Play,
                                onClick = {},
                            ),
                        )
                    }
                    NavigationLayout(
                        state = nav,
                        fabStates = fabs,
                        showBottomBar = true,
                    ) { padding ->
                        Column(
                            Modifier.fillMaxSize().padding(padding).focusRestorer().focusGroup(),
                        ) {
                            Button(onClick = {}) { Text("First item") }
                            Button(onClick = {}) { Text("Second item") }
                        }
                    }
                }
            }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Second item").requestFocus()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.onNodeWithTag("mini_player").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.onNodeWithTag("top_level_fab").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithTag("mini_player").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithText("Second item").assertIsFocused()
        } finally {
            composeRule.runOnIdle {
                service.destroy()
                session.release()
                player.release()
            }
        }
    }

    class TestSessionService : MediaSessionService() {
        lateinit var session: MediaSession
        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
            session
    }
}
