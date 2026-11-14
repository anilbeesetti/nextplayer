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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ForwardingTimeline
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
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
class PlaybackQueueContentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private val feedback = mutableListOf<HapticFeedbackType>()

    @After fun tearDown() = rule.runOnIdle { if (::player.isInitialized) player.release() }

    @Test fun durationIsBelowTheTitleAndBesideTheArtwork() {
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
        assertEquals(
            16f / 10f,
            (artwork.right - artwork.left) / (artwork.bottom - artwork.top),
            .01f,
        )
        val title = rule.onNodeWithTag(
            "queue_title_0",
            true,
        ).getUnclippedBoundsInRoot()
        assertTrue(bounds.left >= artwork.right)
        assertEquals(
            title.left,
            bounds.left,
        )
        assertTrue(bounds.top >= title.bottom)
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

    @Test fun swipeGivesOneLongPressWhenItBreaksFreeAndCancellingKeepsTheQueue() {
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
            assertEquals(
                listOf(HapticFeedbackType.LongPress),
                feedback,
            )
        }
        row.performTouchInput { cancel() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
            assertEquals(
                listOf(HapticFeedbackType.LongPress),
                feedback,
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

    @Test fun longDurationFitsTheSubtitleWithLargeTextInRtl() {
        show(
            LayoutDirection.Rtl,
            fontScale = 2f,
            firstDurationMs = 360_000_000L,
        )
        val art = rule.onNodeWithTag(
            "queue_artwork_0",
            true,
        ).getUnclippedBoundsInRoot()
        val duration = rule.onNodeWithTag(
            "queue_duration_0",
            true,
        )
        duration.assertTextEquals("100:00:00")
        val bounds = duration.getUnclippedBoundsInRoot()
        val title = rule.onNodeWithTag(
            "queue_title_0",
            true,
        ).getUnclippedBoundsInRoot()
        assertTrue(bounds.right <= art.left)
        assertTrue(bounds.top >= title.bottom)
        val layouts = mutableListOf<TextLayoutResult>()
        duration.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(
            layout.layoutInput.text.length,
            layout.getLineEnd(
                layout.lineCount - 1,
                true,
            ),
        )
        repeat(layout.lineCount) { line ->
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(
                layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f,
            )
        }
        assertTrue(layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
        val row = rule.onNodeWithTag(
            "queue_item_0",
            true,
        ).getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom <= row.bottom)
        assertEquals(
            96.dp,
            art.right - art.left,
        )
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

    @Test fun rowsRenderWhileMaskedWindowsShareTheSameUid() {
        show(maskedUids = true)
        rule.onNodeWithTag("queue_item_2").assertExists()
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

    @Test fun shortQueueSwipeResistsAndRevealsOnlyItsOwnWidth() {
        show()
        val row = rule.onNodeWithTag("queue_item_1")
        val before = row.getUnclippedBoundsInRoot()
        val travel = with(rule.density) { 40.dp.toPx() }
        row.performTouchInput {
            down(
                Offset(
                    width * .8f,
                    centerY,
                ),
            )
            repeat(10) {
                moveBy(
                    Offset(
                        -travel / 10,
                        0f,
                    ),
                    32,
                )
            }
        }
        val after = row.getUnclippedBoundsInRoot()
        val reveal = rule.onNodeWithTag(
            "playback_dismiss_reveal",
            true,
        ).getUnclippedBoundsInRoot()
        assertTrue(
            "Early drag should resist movement",
            before.left - after.left <= 20.dp,
        )
        assertTrue(
            "Only the swiped strip should be drawn",
            reveal.right - reveal.left <= 20.dp,
        )
        rule.onRoot().performTouchInput { cancel() }
        rule.runOnIdle {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
    }

    @Test fun rowCatchesUpWhileTheFingerKeepsMovingPastTheResistance() {
        show()
        val row = rule.onNodeWithTag("queue_item_1")
        val start = row.getUnclippedBoundsInRoot().left
        val step = 6.dp
        val stepPx = with(rule.density) { step.toPx() }
        rule.mainClock.autoAdvance = false
        row.performTouchInput {
            down(
                Offset(
                    width * .9f,
                    centerY,
                ),
            )
            moveBy(
                Offset(
                    -viewConfiguration.touchSlop - stepPx,
                    0f,
                ),
                16,
            )
        }
        rule.mainClock.advanceTimeByFrame()
        repeat(30) {
            rule.onRoot().performTouchInput {
                moveBy(
                    Offset(
                        -stepPx,
                        0f,
                    ),
                    16,
                )
            }
            rule.mainClock.advanceTimeByFrame()
        }
        val finger = step * 31
        val moved = start - row.getUnclippedBoundsInRoot().left
        assertTrue(
            "Row moved $moved while the finger moved $finger",
            moved >= finger * .7f,
        )
        rule.onRoot().performTouchInput { cancel() }
        rule.mainClock.autoAdvance = true
    }

    @Test fun returnBounceNeverShowsTheRemovalAreaOnTheOppositeSide() {
        show()
        val row = rule.onNodeWithTag("queue_item_1")
        val rest = row.getUnclippedBoundsInRoot()
        val center = (rest.left + rest.right) / 2
        row.performTouchInput {
            down(
                Offset(
                    width * .9f,
                    centerY,
                ),
            )
            repeat(10) {
                moveBy(
                    Offset(
                        -width * .03f,
                        0f,
                    ),
                    32,
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { up() }
        var overshot = false
        repeat(90) {
            rule.mainClock.advanceTimeByFrame()
            overshot = overshot || row.getUnclippedBoundsInRoot().left > rest.left
            rule.onAllNodesWithTag(
                "playback_dismiss_reveal",
                true,
            ).fetchSemanticsNodes().forEach { reveal ->
                val rowCenter = with(rule.density) { center.toPx() }
                assertTrue(
                    "Removal area ${reveal.boundsInRoot} is left of the row center $rowCenter",
                    reveal.boundsInRoot.isEmpty || reveal.boundsInRoot.left >= rowCenter,
                )
            }
        }
        rule.mainClock.autoAdvance = true
        assertTrue(
            "The return spring should overshoot for this check to mean anything",
            overshot,
        )
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
        maskedUids: Boolean = false,
        firstDurationMs: Long = 15_000L,
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
                            ).setDurationMs(firstDurationMs + i * 5_000).build(),
                        )
                        .build()
                },
            )
        }
        val display = if (maskedUids) {
            object : ForwardingPlayer(player) {
                override fun getCurrentTimeline(): Timeline =
                    object : ForwardingTimeline(super.getCurrentTimeline()) {
                        override fun getWindow(
                            windowIndex: Int,
                            window: Timeline.Window,
                            defaultPositionProjectionUs: Long,
                        ): Timeline.Window = super.getWindow(
                            windowIndex,
                            window,
                            defaultPositionProjectionUs,
                        ).apply { uid = 0 }
                    }
            }
        } else if (editable) {
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
