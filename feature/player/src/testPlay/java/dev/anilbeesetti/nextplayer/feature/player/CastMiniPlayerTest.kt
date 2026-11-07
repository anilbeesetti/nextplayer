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
class CastMiniPlayerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val player = MiniPlayerTestPlayer()
    private var opened = 0
    private var stopped = 0
    private var transferred = 0

    @After
    fun tearDown() = composeRule.runOnIdle { player.release() }

    @Test
    fun castingOpensSheetAndTransferToPhoneDismissesIt() {
        composeRule.runOnIdle { player.remote(true) }
        showPlayer()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.onNodeWithTag("cast_controls").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.play_on_phone))
            .performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(
                1,
                transferred,
            )
        }
        composeRule.onNodeWithTag("cast_controls").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                0,
                opened,
            )
            assertEquals(
                1,
                transferred,
            )
        }
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.runOnIdle {
            assertEquals(
                1,
                opened,
            )
        }
    }

    @Test
    fun castSheetUsesSessionSeekVolumeAndQueueCommands() {
        composeRule.runOnIdle { player.remote(true) }
        showPlayer()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.onNodeWithContentDescription(text(R.string.player_controls_progress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        composeRule.runOnIdle {
            assertEquals(
                30_000L,
                player.currentPosition,
            )
        }
        composeRule.onNodeWithContentDescription(text(R.string.cast_volume))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        composeRule.runOnIdle {
            assertEquals(
                75,
                player.deviceVolume,
            )
        }
        composeRule.onNodeWithContentDescription(text(R.string.player_controls_next)).performClick()
        composeRule.onAllNodesWithText(
            "Movie 2",
            useUnmergedTree = true,
        ).onFirst().assertExists()
        composeRule.runOnIdle {
            assertEquals(
                1,
                player.currentMediaItemIndex,
            )
        }
    }

    @Test
    fun unsupportedCommandsAreDisabled() {
        composeRule.runOnIdle {
            player.remote(true)
            player.update {
                setAvailableCommands(
                    Player.Commands.Builder().addAll(
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                    ).build(),
                )
            }
        }
        showPlayer()
        composeRule.onNodeWithContentDescription(text(R.string.play)).assertIsNotEnabled()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.onNodeWithContentDescription(
            text(R.string.player_controls_progress),
        ).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            text(R.string.player_controls_next),
        ).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.cast_volume)).assertDoesNotExist()
    }

    @Test
    fun unknownDurationHasSafeProgressAndDisablesSeeking() {
        composeRule.runOnIdle {
            player.remote(true)
            player.update {
                setPlaylist(
                    listOf(
                        player.item(
                            0,
                            C.TIME_UNSET,
                        ),
                    ),
                )
            }
            assertEquals(
                0f,
                PlaybackSnapshot.from(player).progress,
            )
        }
        showPlayer()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.onNodeWithContentDescription(
            text(R.string.player_controls_progress),
        ).assertIsNotEnabled()
    }

    @Test
    fun largeTextAndRtlKeepControlsReachable() {
        composeRule.runOnIdle { player.remote(true) }
        showPlayer(largeTextAndRtl = true)
        composeRule.onNodeWithContentDescription(text(R.string.play)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            text(R.string.stop_player_session),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.onNodeWithText(text(R.string.play_on_phone))
            .performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(
                1,
                transferred,
            )
        }
    }

    private fun showPlayer(largeTextAndRtl: Boolean = false) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density.density,
                    if (largeTextAndRtl) 2f else density.fontScale,
                ),
                LocalLayoutDirection provides if (largeTextAndRtl) {
                    LayoutDirection.Rtl
                } else {
                    LocalLayoutDirection.current
                },
            ) {
                NextPlayerTheme {
                    MiniPlayer(
                        player = player,
                        onOpenPlayer = { opened++ },
                        onStop = { stopped++ },
                        onPlayOnPhone = {
                            transferred++
                            player.remote(false)
                        },
                        deviceName = "Living room TV",
                    )
                }
            }
        }
    }

    private fun text(id: Int) = composeRule.activity.getString(id)
}
