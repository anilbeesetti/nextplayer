package dev.anilbeesetti.nextplayer.feature.player

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MiniPlayerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val player = MiniPlayerTestPlayer()
    private var opened = 0
    private var stopped = 0

    @After
    fun tearDown() = composeRule.runOnIdle { player.release() }

    @Test
    fun pausedPlaybackStaysVisibleAndCanPlayAndPause() {
        showPlayer()
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.play)).performClick()
        composeRule.runOnIdle { assertTrue(player.playWhenReady) }
        composeRule.onNodeWithContentDescription(text(R.string.pause_playback)).performClick()
        composeRule.runOnIdle { assertFalse(player.playWhenReady) }
    }

    @Test
    fun bufferingPlaybackCanBePaused() {
        composeRule.runOnIdle {
            player.update {
                setPlaybackState(Player.STATE_BUFFERING).setPlayWhenReady(
                    true,
                    1,
                )
            }
        }
        showPlayer()
        composeRule.onNodeWithContentDescription(text(R.string.pause_playback)).performClick()
        composeRule.runOnIdle { assertFalse(player.playWhenReady) }
    }

    @Test
    fun localTapOpensPlayerAndCloseDoesNotOpenIt() {
        showPlayer()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.onNodeWithContentDescription(text(R.string.stop_player_session)).performClick()
        composeRule.runOnIdle {
            assertEquals(
                1,
                opened,
            )
            assertEquals(
                1,
                stopped,
            )
        }
    }

    @Test
    fun emptyAndCompletedQueuesAreHidden() {
        showPlayer()
        composeRule.runOnIdle { player.update { setPlaybackState(Player.STATE_ENDED) } }
        composeRule.onNodeWithTag("mini_player").assertDoesNotExist()
        composeRule.runOnIdle {
            player.update {
                setPlaylist(
                    emptyList(),
                ).setCurrentMediaItemIndex(C.INDEX_UNSET).setPlaybackState(Player.STATE_IDLE)
            }
        }
        composeRule.onNodeWithTag("mini_player").assertDoesNotExist()
    }

    @Test
    fun progressStaysBoundedAfterTimelineChanges() {
        composeRule.runOnIdle {
            player.update { setContentPositionMs(120_000) }
            assertEquals(
                1f,
                PlaybackSnapshot.from(player).progress,
            )
            player.update {
                setPlaylist(
                    listOf(
                        player.item(
                            0,
                            0,
                        ),
                    ),
                )
            }
            assertEquals(
                0f,
                PlaybackSnapshot.from(player).progress,
            )
        }
    }

    private fun showPlayer() {
        composeRule.setContent {
            NextPlayerTheme {
                MiniPlayer(
                    player = player,
                    onOpenPlayer = { opened++ },
                    onStop = { stopped++ },
                    onPlayOnPhone = {},
                )
            }
        }
    }

    private fun text(id: Int) = composeRule.activity.getString(id)
}
