package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackQueueContentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private val feedback = mutableListOf<HapticFeedbackType>()

    @After fun tearDown() = rule.runOnIdle { if (::player.isInitialized) player.release() }

    @Test fun durationIsOnTheLargerArtwork() {
        show()
        val artwork = rule.onNodeWithTag(
            "queue_artwork_0",
            true,
        ).getUnclippedBoundsInRoot()
        val duration = rule.onNodeWithTag(
            "queue_duration_0",
            true,
        )
        duration.assertTextEquals("00:15")
        val bounds = duration.getUnclippedBoundsInRoot()
        assertTrue(artwork.right - artwork.left >= 56.dp)
        assertTrue(bounds.left >= artwork.left && bounds.right <= artwork.right)
        assertTrue(bounds.top >= artwork.top && bounds.bottom <= artwork.bottom)
    }

    @Test fun swipeRemovesOnlyTheRequestedOccurrenceAndKeepsTheLastItem() {
        show()
        rule.onNodeWithTag("queue_item_1").performTouchInput { swipeLeft() }
        rule.runOnIdle {
            assertEquals(
                2,
                player.mediaItemCount,
            )
            assertEquals(
                "last",
                player.getMediaItemAt(1).mediaId,
            )
        }
        rule.onNodeWithTag("queue_item_0").performTouchInput { swipeLeft() }
        rule.runOnIdle {
            assertEquals(
                1,
                player.mediaItemCount,
            )
        }
        rule.onNodeWithTag("queue_item_0").performTouchInput { swipeLeft() }
        rule.runOnIdle {
            assertEquals(
                1,
                player.mediaItemCount,
            )
        }
    }

    @Test fun crossingRemoveThresholdVibratesBeforeReleaseAndCancellingKeepsTheQueue() {
        show()
        val row = rule.onNodeWithTag("queue_item_1")
        row.performTouchInput {
            down(
                Offset(
                    width * .9f,
                    height / 2f,
                ),
            )
            repeat(12) { step ->
                moveTo(
                    Offset(
                        width * (.9f - .8f * (step + 1) / 12),
                        height / 2f,
                    ),
                    32,
                )
            }
        }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
            assertTrue(feedback.contains(HapticFeedbackType.GestureThresholdActivate))
        }
        row.performTouchInput { cancel() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun rtlSwipeRevealsTheIconOnThePhysicalLeft() {
        show(LayoutDirection.Rtl)
        val row = rule.onNodeWithTag("queue_item_1")
        row.performTouchInput {
            down(
                Offset(
                    width * .1f,
                    centerY,
                ),
            )
            repeat(12) { step ->
                moveTo(
                    Offset(
                        width * (.1f + .6f * (step + 1) / 12),
                        centerY,
                    ),
                    32,
                )
            }
        }
        val reveal = rule.onNodeWithTag(
            "playback_dismiss_reveal",
            true,
        ).getUnclippedBoundsInRoot()
        val icon = rule.onNodeWithTag(
            "playback_dismiss_icon",
            true,
        ).getUnclippedBoundsInRoot()
        assertTrue(icon.right < (reveal.left + reveal.right) / 2)
        rule.onRoot().performTouchInput { cancel() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun rtlRemovalUsesEndToStart() {
        show(LayoutDirection.Rtl)
        rule.onNodeWithTag("queue_item_1").performTouchInput { swipeRight() }
        rule.runOnIdle {
            assertEquals(
                2,
                player.mediaItemCount,
            )
        }
    }

    @Test fun dragHandleMovesContinuouslyAcrossTwoItemsWithoutRemoving() {
        show()
        val row = rule.onNodeWithTag("queue_item_2").getUnclippedBoundsInRoot()
        val travel = with(rule.density) { (row.bottom - row.top + 8.dp).toPx() * 2 }
        val handle = rule.onNodeWithTag(
            "queue_drag_2",
            true,
        )
        handle.performTouchInput { down(center) }
        repeat(20) {
            rule.onRoot().performTouchInput {
                moveBy(
                    Offset(
                        0f,
                        -travel / 20,
                    ),
                    32,
                )
            }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
            assertEquals(
                "last",
                player.getMediaItemAt(0).mediaId,
            )
        }
        rule.onRoot().performTouchInput { up() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun draggingTheHandleHorizontallyDoesNotRemoveAnItem() {
        show()
        rule.onNodeWithTag(
            "queue_drag_1",
            true,
        ).performTouchInput { swipeLeft() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun readOnlyQueueCannotBeRemovedBySwipeOrButtons() {
        show(editable = false)
        rule.onAllNodesWithContentDescription(
            rule.activity.getString(R.string.remove),
        ).assertCountEquals(0)
        rule.onNodeWithTag("queue_item_1").performTouchInput { swipeLeft() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun largeTextDurationStaysInsideArtworkInRtl() {
        show(
            LayoutDirection.Rtl,
            fontScale = 2f,
        )
        val art = rule.onNodeWithTag(
            "queue_artwork_0",
            true,
        ).getUnclippedBoundsInRoot()
        val text = rule.onNodeWithTag(
            "queue_duration_0",
            true,
        ).getUnclippedBoundsInRoot()
        assertTrue(text.left >= art.left && text.right <= art.right)
        assertTrue(text.top >= art.top && text.bottom <= art.bottom)
    }

    @Test fun fullscreenVideoQueueUsesTheSameRemovalAndDurationControls() {
        show(fullscreen = true)
        rule.onNodeWithTag(
            "queue_duration_0",
            true,
        ).assertTextEquals("00:15")
        rule.onNodeWithTag("queue_item_1").performTouchInput { swipeLeft() }
        rule.runOnIdle {
            assertEquals(
                2,
                player.mediaItemCount,
            )
        }
    }

    @Test fun shortFastFlickBelowTheThresholdKeepsTheItem() {
        show()
        rule.onNodeWithTag("queue_item_1").performTouchInput {
            swipe(
                start = Offset(
                    width * .8f,
                    centerY,
                ),
                end = Offset(
                    width * .55f,
                    centerY,
                ),
                durationMillis = 60,
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun queueSwipeFollowsTheFingerWithoutStallOrJumpAndRevealsOnlyItsOwnWidth() {
        show()
        val row = rule.onNodeWithTag("queue_item_1")
        val initialLeft = row.getUnclippedBoundsInRoot().left
        val step = 10.dp
        val stepPx = with(rule.density) { step.toPx() }
        row.performTouchInput {
            down(
                Offset(
                    width * .8f,
                    centerY,
                ),
            )
            moveBy(
                Offset(
                    -viewConfiguration.touchSlop - stepPx,
                    0f,
                ),
                32,
            )
        }
        repeat(12) { index ->
            val before = row.getUnclippedBoundsInRoot().left
            rule.onRoot().performTouchInput {
                moveBy(
                    Offset(
                        -stepPx,
                        0f,
                    ),
                    32,
                )
            }
            val moved = before - row.getUnclippedBoundsInRoot().left
            assertTrue(
                "Step $index moved $moved instead of following the finger by $step",
                (moved - step).value in -1f..1f,
            )
        }
        val travelled = initialLeft - row.getUnclippedBoundsInRoot().left
        val reveal = rule.onNodeWithTag(
            "playback_dismiss_reveal",
            true,
        ).getUnclippedBoundsInRoot()
        assertTrue(
            "Only the swiped strip should be drawn",
            ((reveal.right - reveal.left) - travelled).value in -1f..1f,
        )
        rule.onRoot().performTouchInput { cancel() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun removeButtonIsShownForKeyboardFocusInsteadOfDuplicatingSwipeIcon() {
        show()
        val label = rule.activity.getString(R.string.remove)
        rule.onAllNodesWithContentDescription(label).assertCountEquals(0)
        val row = rule.onNodeWithTag("queue_item_1")
        row.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        rule.onAllNodesWithContentDescription(label).assertCountEquals(1)
    }

    @Test fun queueRemovalShowsTitleAndUndoWithoutChangingPlayback() {
        show()
        rule.onNodeWithTag("queue_item_1").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("queue_removal_undo").assertIsDisplayed()
        rule.onNodeWithText(
            rule.activity.getString(R.string.queue_video_removed),
        ).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.undo)).performClick()
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
            assertEquals(
                "same",
                player.getMediaItemAt(1).mediaId,
            )
            assertEquals(
                0,
                player.currentMediaItemIndex,
            )
            assertTrue(!player.playWhenReady)
        }
    }

    private fun show(
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        editable: Boolean = true,
        fullscreen: Boolean = false,
    ) {
        rule.runOnUiThread {
            player = ExoPlayer.Builder(rule.activity).build()
            player.setMediaItems(
                listOf(
                    "same",
                    "same",
                    "last",
                ).mapIndexed { i, id ->
                    MediaItem.Builder().setMediaId(id).setUri("file:///$id.mp4")
                        .setMediaMetadata(
                            MediaMetadata.Builder().setTitle(
                                id,
                            ).setDurationMs(15_000L + i * 5_000).build(),
                        )
                        .build()
                },
            )
        }
        val display = if (editable) {
            player
        } else {
            object : ForwardingPlayer(player) {
                override fun isCommandAvailable(command: Int): Boolean =
                    command != Player.COMMAND_CHANGE_MEDIA_ITEMS &&
                        super.isCommandAvailable(command)
                override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands()
                    .buildUpon().remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).build()
            }
        }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density.density,
                    fontScale,
                ),
                LocalLayoutDirection provides direction,
                LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        feedback += hapticFeedbackType
                    }
                },
            ) {
                NextPlayerTheme {
                    if (fullscreen) {
                        Box(Modifier.fillMaxSize()) {
                            PlaylistView(
                                show = true,
                                player = display,
                            )
                        }
                    } else {
                        PlaybackQueueContent(display)
                    }
                }
            }
        }
    }
}
