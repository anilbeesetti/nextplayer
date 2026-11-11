package dev.anilbeesetti.nextplayer.feature.player

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaybackSnapshot
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
class PlaybackOverlayTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val player = MiniPlayerTestPlayer()
    private var expanded by mutableStateOf(false)
    private var hidden by mutableStateOf(false)
    private var dismissed = 0
    private var watched = 0
    private val commands = mutableListOf<CustomCommands>()

    @After fun tearDown() = composeRule.runOnIdle { player.release() }

    @Test fun tapAndBackExpandAndCollapseTheSameSession() {
        show()
        composeRule.onNodeWithTag("mini_player").performSemanticsAction(SemanticsActions.OnClick) {
            it()
        }
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.watch_video)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(
                1,
                watched,
            )
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
    }

    @Test fun artworkMorphTracksExpansionAndCanReverseBeforeSettling() {
        show()
        val compact = composeRule.onNodeWithTag(
            "compact_artwork",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag(
                "mini_player",
            ).performSemanticsAction(SemanticsActions.OnClick) {
                it()
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            repeat(8) {
                composeRule.mainClock.advanceTimeBy(16)
                composeRule.waitForIdle()
                assertTransitionMetadataDoesNotCoverArtwork()
            }
            val mid = composeRule.onNodeWithTag(
                "transition_artwork",
                useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()
            assertTrue(mid.right - mid.left > compact.right - compact.left)
            assertTrue(mid.top < compact.top)
            composeRule.onNodeWithText(text(R.string.watch_video)).performClick()
            composeRule.runOnIdle {
                assertEquals(
                    0,
                    watched,
                )
            }
            composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.mainClock.advanceTimeBy(100)
            val reversed = composeRule.onNodeWithTag(
                "transition_artwork",
                useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()
            assertTrue(reversed.right - reversed.left < mid.right - mid.left)
            composeRule.mainClock.advanceTimeBy(700)
            composeRule.onNodeWithTag(
                "transition_artwork",
                useUnmergedTree = true,
            ).assertDoesNotExist()
            assertEquals(
                compact,
                composeRule.onNodeWithTag(
                    "compact_artwork",
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot(),
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test fun rtlArtworkMorphUsesPhysicalBoundsWithoutMirroringTwice() {
        show(largeText = true)
        val compact = composeRule.onNodeWithTag(
            "compact_artwork",
            useUnmergedTree = true,
        )
            .getUnclippedBoundsInRoot()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag(
                "mini_player",
            ).performSemanticsAction(SemanticsActions.OnClick) {
                it()
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            repeat(8) {
                composeRule.mainClock.advanceTimeBy(16)
                composeRule.waitForIdle()
                assertTransitionMetadataDoesNotCoverArtwork()
            }
            val mid = composeRule.onNodeWithTag(
                "transition_artwork",
                useUnmergedTree = true,
            )
                .getUnclippedBoundsInRoot()
            val target = composeRule.onNodeWithTag("playback_artwork").getUnclippedBoundsInRoot()
            assertTrue(
                mid.left >= minOf(
                    compact.left,
                    target.left,
                ),
            )
            assertTrue(
                mid.left <= maxOf(
                    compact.left,
                    target.left,
                ),
            )
            assertTrue(mid.right <= composeRule.onRoot().getUnclippedBoundsInRoot().right)
        } finally {
            composeRule.mainClock.advanceTimeBy(700)
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test fun replacingQueueDuringDismissalDoesNotStopTheReplacement() {
        show()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("mini_player").performTouchInput { swipeLeft() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.runOnIdle {
                player.update {
                    setPlaylist(
                        listOf(
                            player.item(3),
                            player.item(4),
                        ),
                    )
                }
            }
            composeRule.mainClock.advanceTimeBy(700)
            composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
            composeRule.runOnIdle {
                assertEquals(
                    0,
                    dismissed,
                )
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test fun fastSwipeExitFollowsTheRawFingerDirection() {
        show()
        val mini = composeRule.onNodeWithTag("mini_player")
        val before = mini.getUnclippedBoundsInRoot()
        composeRule.mainClock.autoAdvance = false
        try {
            mini.performTouchInput {
                down(
                    Offset(
                        width * .9f,
                        centerY,
                    ),
                )
                moveTo(
                    Offset(
                        width * .1f,
                        centerY,
                    ),
                    16,
                )
                up()
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.waitForIdle()
            assertTrue(mini.getUnclippedBoundsInRoot().left < before.left)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test fun compactHorizontalSwipesDismissInBothDirections() {
        show()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeLeft() }
        composeRule.runOnIdle { hidden = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { hidden = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeRight() }
        composeRule.runOnIdle {
            assertEquals(
                2,
                dismissed,
            )
            assertEquals(
                false,
                expanded,
            )
        }
    }

    @Test fun upwardDragExpandsAndQueueBackPrecedesCollapse() {
        show()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
        composeRule.onNodeWithText(
            text(R.string.now_playing),
            substring = false,
        )
        composeRule.onNodeWithTag("playback_artwork").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("playback_queue").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("playback_queue").assertDoesNotExist()
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
    }

    @Test fun connectingDisablesPlaybackAndOffersCancel() {
        expanded = true
        show(PlaybackConnection.CONNECTING)
        composeRule.onNodeWithContentDescription(
            text(R.string.play),
        ).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            text(R.string.player_controls_progress),
        ).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.cancel)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(CustomCommands.CANCEL_CAST),
                commands,
            )
        }
    }

    @Test fun lostConnectionOffersExplicitRecoveryWithoutPlaying() {
        expanded = true
        show(PlaybackConnection.LOST)
        composeRule.onNodeWithText(text(R.string.retry)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.play_on_phone)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    CustomCommands.RETRY_CAST,
                    CustomCommands.PLAY_ON_PHONE,
                ),
                commands,
            )
            assertEquals(
                false,
                player.playWhenReady,
            )
        }
    }

    @Test fun vaultPinHidesBothPlaybackPresentations() {
        show()
        composeRule.runOnIdle { hidden = true }
        composeRule.onNodeWithTag("mini_player").assertDoesNotExist()
        composeRule.runOnIdle { expanded = true }
        composeRule.onNodeWithTag("expanded_player").assertDoesNotExist()
        composeRule.runOnIdle { hidden = false }
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
    }

    @Test fun largeTextAndRtlKeepExpandedActionsReachable() {
        expanded = true
        show(largeText = true)
        composeRule.onNodeWithContentDescription(text(R.string.collapse_player)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.watch_video)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            text(R.string.stop_player_session),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test fun slowPartialExpansionFollowsFingerAndReversesWithoutDisposal() {
        show()
        composeRule.onNodeWithTag("playback_sheet").assertDoesNotExist()
        val before = composeRule.onNodeWithTag(
            "mini_player",
        ).fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithTag("mini_player").performTouchInput {
            down(
                Offset(
                    50f,
                    centerY,
                ),
            )
            moveBy(
                Offset(
                    0f,
                    -150f,
                ),
                delayMillis = 500,
            )
        }
        val during = composeRule.onNodeWithTag(
            "playback_sheet",
        ).fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Sheet must follow the finger: before=$before during=$during",
            during < before - 50f,
        )
        composeRule.onRoot().performTouchInput {
            moveBy(
                Offset(
                    0f,
                    150f,
                ),
                delayMillis = 500,
            )
            up()
        }
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                false,
                expanded,
            )
        }
    }

    @Test fun artworkMetadataEnrichmentAndDuplicateItemsDoNotSeekOrResetThePage() {
        expanded = true
        composeRule.runOnIdle {
            player.update {
                setPlaylist(
                    listOf(
                        player.item(0),
                        player.item(1).buildUpon()
                            .setMediaItem(player.item(0).mediaItem).build(),
                    ),
                )
                setCurrentMediaItemIndex(1)
            }
        }
        show()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle {
            player.update {
                setPlaylist(
                    listOf(
                        player.item(0),
                        player.item(1).buildUpon()
                            .setMediaItem(player.item(0).mediaItem)
                            .setMediaMetadata(
                                MediaMetadata.Builder().setTitle("Enriched").build(),
                            ).build(),
                    ),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle {
            assertEquals(
                1,
                player.currentMediaItemIndex,
            )
            assertTrue(player.seeks.isEmpty())
        }
    }

    @Test fun artworkSwipeSelectsNextWithoutDismissing() {
        expanded = true
        show()
        composeRule.onNodeWithTag("playback_artwork").performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals(
                1,
                player.currentMediaItemIndex,
            )
            assertEquals(
                0,
                dismissed,
            )
        }
    }

    @Test fun expandedSeekAndOutputVolumeUseSessionCommands() {
        expanded = true
        composeRule.runOnIdle { player.remote(true) }
        show(PlaybackConnection.CAST)
        composeRule.onNodeWithContentDescription(text(R.string.player_controls_progress))
            .performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        composeRule.runOnIdle {
            assertEquals(
                30_000,
                player.currentPosition,
            )
        }
        composeRule.onNodeWithContentDescription(
            text(R.string.playback_output),
        ).assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(text(R.string.cast_volume))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        composeRule.runOnIdle {
            assertEquals(
                75,
                player.deviceVolume,
            )
        }
    }

    @Test fun unavailableSeekAndPlaybackCommandsStayDisabled() {
        expanded = true
        composeRule.runOnIdle {
            player.update {
                setAvailableCommands(
                    Player.Commands.Builder().addAll(
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                    ).build(),
                )
            }
        }
        show()
        composeRule.onNodeWithContentDescription(
            text(R.string.play),
        ).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            text(R.string.player_controls_progress),
        ).assertIsNotEnabled()
    }

    @Test fun artworkDownwardDragCrossesHalfwayWithoutChangingLayoutOrLosingGesture() {
        expanded = true
        show()
        val sheet = composeRule.onNodeWithTag("playback_sheet")
        val artwork = composeRule.onNodeWithTag("playback_artwork")
        val height = sheet.fetchSemanticsNode().boundsInRoot.height
        artwork.performTouchInput {
            down(center)
            moveBy(
                Offset(
                    0f,
                    height * .6f,
                ),
                delayMillis = 700,
            )
        }
        val during = sheet.fetchSemanticsNode().boundsInRoot.top
        assertTrue(during > height * .45f)
        artwork.performTouchInput { up() }
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                false,
                expanded,
            )
        }
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    fun landscapeUsesFullHeightAndKeepsOutputAndAllControlsVisible() {
        expanded = true
        show()
        composeRule.onNodeWithTag("playback_header").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(text(R.string.collapse_player)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.playback_output)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            text(R.string.stop_player_session),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.watch_video)).assertIsDisplayed()
        val artwork = composeRule.onNodeWithTag("playback_artwork").getUnclippedBoundsInRoot()
        val metadata = composeRule.onNodeWithTag("playback_metadata").getUnclippedBoundsInRoot()
        val actions = composeRule.onNodeWithTag(
            "playback_output_actions",
        ).getUnclippedBoundsInRoot()
        assertTrue(artwork.right < metadata.left)
        assertTrue(actions.top >= metadata.top && actions.bottom <= metadata.bottom)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun narrowLandscapeRetainsMetadataWithALongCastReceiverName() {
        expanded = true
        composeRule.runOnIdle { player.remote(true) }
        show(
            PlaybackConnection.CAST,
            deviceName = "Living room receiver with a very long name",
        )
        val title = composeRule.onNodeWithTag("playback_title").getUnclippedBoundsInRoot()
        assertTrue(title.right - title.left >= 120.dp)
        val output = composeRule.onNodeWithTag("playback_output_action").getUnclippedBoundsInRoot()
        assertEquals(
            50.dp,
            output.right - output.left,
        )
        composeRule.onNodeWithContentDescription(text(R.string.playback_output)).assertIsDisplayed()
        composeRule.onNodeWithText(
            text(R.string.play_on_phone),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test fun portraitHeaderUsesWideSeparatedOutputAndQueueChips() {
        expanded = true
        show()
        composeRule.onNodeWithTag("playback_header").assertIsDisplayed()
        assertEquals(
            Role.Button,
            composeRule.onNodeWithTag(
                "playback_output_action",
            ).fetchSemanticsNode().config[SemanticsProperties.Role],
        )
        val output = composeRule.onNodeWithTag("playback_output_action").getUnclippedBoundsInRoot()
        val queue = composeRule.onNodeWithTag("playback_queue_action").getUnclippedBoundsInRoot()
        assertEquals(
            50.dp,
            output.right - output.left,
        )
        assertEquals(
            50.dp,
            queue.right - queue.left,
        )
        assertEquals(
            6.dp,
            queue.left - output.right,
        )
    }

    @Test fun seekHasOneAccessibleProgressNodeAndDoesNotCollapseWhileScrubbing() {
        expanded = true
        show()
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        )
            .assertCountEquals(1)
        val initial = player.currentPosition
        composeRule.onNodeWithContentDescription(text(R.string.player_controls_progress))
            .performScrollTo().performTouchInput {
                down(center)
                moveBy(
                    Offset(
                        width * .2f,
                        height * .2f,
                    ),
                    delayMillis = 400,
                )
                up()
            }
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(player.currentPosition > initial) }
    }

    @Test fun seekTrackDoesNotMoveDuringScrubbingAndAlignsWithTimeLabels() {
        expanded = true
        show()
        val progress = composeRule.onNodeWithContentDescription(
            text(R.string.player_controls_progress),
        )
        progress.performScrollTo()
        val track = composeRule.onNodeWithTag(
            "playback_seek_track",
            useUnmergedTree = true,
        )
        val before = track.getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithTag("playback_position").getUnclippedBoundsInRoot()
        assertEquals(
            label.left,
            before.left,
        )
        progress.performTouchInput {
            down(center)
            moveBy(
                Offset(
                    width * .15f,
                    0f,
                ),
                delayMillis = 300,
            )
        }
        try {
            assertEquals(
                before,
                track.getUnclippedBoundsInRoot(),
            )
        } finally {
            progress.performTouchInput { up() }
        }
        assertEquals(
            before,
            track.getUnclippedBoundsInRoot(),
        )
    }

    @Test fun outputChipTransitionsBetweenLocalAndNamedReceiverWithoutCrowding() {
        expanded = true
        show(deviceName = "Living room receiver with a long name")
        val output = composeRule.onNodeWithTag("playback_output_action")
        val local = output.getUnclippedBoundsInRoot()
        assertEquals(
            50.dp,
            local.right - local.left,
        )
        composeRule.runOnIdle { player.remote(true) }
        composeRule.onNodeWithTag("playback_header_title").assertDoesNotExist()
        val remote = output.getUnclippedBoundsInRoot()
        assertTrue(remote.right - remote.left > 50.dp)
        assertTrue(remote.right - remote.left <= 190.dp)
        composeRule.onNodeWithTag("playback_queue_action").assertIsDisplayed()
        composeRule.runOnIdle { player.remote(false) }
        composeRule.onNodeWithTag("playback_header_title").assertIsDisplayed()
        val restored = output.getUnclippedBoundsInRoot()
        assertEquals(
            50.dp,
            restored.right - restored.left,
        )
    }

    @Test fun portraitCastHeaderLeavesRoomForReceiverInsteadOfNowPlaying() {
        expanded = true
        composeRule.runOnIdle { player.remote(true) }
        show(
            PlaybackConnection.CAST,
            deviceName = "Living room receiver with a long name",
        )
        composeRule.onNodeWithTag("playback_header_title").assertDoesNotExist()
        composeRule.onNodeWithTag("playback_output_action").assertIsDisplayed()
        composeRule.onNodeWithTag("playback_queue_action").assertIsDisplayed()
    }

    @Test
    fun portraitArtworkPagerSpansTheScreenAroundAnInsetArtwork() {
        expanded = true
        show()
        val root = composeRule.onNodeWithTag("expanded_player").getUnclippedBoundsInRoot()
        val pager = composeRule.onNodeWithTag("playback_artwork_pager").getUnclippedBoundsInRoot()
        val artwork = composeRule.onNodeWithTag("playback_artwork").getUnclippedBoundsInRoot()
        assertEquals(
            root.left,
            pager.left,
        )
        assertEquals(
            root.right,
            pager.right,
        )
        assertEquals(
            (root.left + 24.dp).value,
            artwork.left.value,
            .5f,
        )
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    fun landscapeArtworkPagerReachesTheScreenEdge() {
        expanded = true
        show()
        val root = composeRule.onNodeWithTag("expanded_player").getUnclippedBoundsInRoot()
        val pager = composeRule.onNodeWithTag("playback_artwork_pager").getUnclippedBoundsInRoot()
        val frame = composeRule.onNodeWithTag("landscape_artwork_frame").getUnclippedBoundsInRoot()
        assertEquals(
            root.left,
            pager.left,
        )
        assertTrue(frame.left >= root.left + 24.dp)
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    fun landscapeCollapseIsInsetInsideTheArtworkFrame() {
        expanded = true
        show()
        val frame = composeRule.onNodeWithTag("landscape_artwork_frame").getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithTag("landscape_collapse").getUnclippedBoundsInRoot()
        assertTrue(button.left >= frame.left + 8.dp && button.top >= frame.top + 8.dp)
        assertTrue(button.right <= frame.right && button.bottom <= frame.bottom)
    }

    @Test
    @Config(qualifiers = "w1400dp-h900dp-land")
    fun largeLandscapeArtworkFitsAndDoesNotRescaleOnPlayPause() {
        expanded = true
        show()
        composeRule.mainClock.advanceTimeBy(600)
        val frame = composeRule.onNodeWithTag("landscape_artwork_frame").getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithTag("landscape_collapse").getUnclippedBoundsInRoot()
        assertTrue(frame.right - frame.left > 440.dp)
        assertEquals(
            (frame.left + 8.dp).value,
            button.left.value,
            .5f,
        )
        assertEquals(
            (frame.top + 8.dp).value,
            button.top.value,
            .5f,
        )
        val metadata = composeRule.onNodeWithTag("playback_metadata").getUnclippedBoundsInRoot()
        val root = composeRule.onNodeWithTag("expanded_player").getUnclippedBoundsInRoot()
        assertTrue(frame.left >= root.left && frame.top >= root.top)
        assertTrue(frame.right <= metadata.left && frame.bottom <= root.bottom)
        assertTrue(button.right <= frame.right)
        assertTrue(button.bottom <= frame.bottom)
        assertTrue(button.right - button.left >= 48.dp)
        composeRule.runOnIdle { player.play() }
        composeRule.mainClock.advanceTimeBy(600)
        assertEquals(
            frame,
            composeRule.onNodeWithTag("landscape_artwork_frame").getUnclippedBoundsInRoot(),
        )
        assertEquals(
            button,
            composeRule.onNodeWithTag("landscape_collapse").getUnclippedBoundsInRoot(),
        )
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun longLandscapeTitleScrollsOnlyDuringPlaybackWithSystemMotionEnabled() {
        val resolver = composeRule.activity.contentResolver
        val original = Settings.Global.getString(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
        )
        val title = "Very long video filename with a distinguishing episode number.mp4"
        composeRule.runOnIdle {
            Settings.Global.putFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            player.update {
                setPlaylist(
                    listOf(
                        player.item(0).buildUpon().setMediaMetadata(
                            MediaMetadata.Builder().setTitle(title).build(),
                        ).build(),
                        player.item(1),
                    ),
                )
            }
        }
        expanded = true
        show()
        fun titleIsBounded(): Boolean {
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(
                title,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            return layouts.single().layoutInput.constraints.hasBoundedWidth
        }
        try {
            composeRule.onNodeWithTag("playback_header").assertDoesNotExist()
            composeRule.runOnIdle {
                assertEquals(
                    false,
                    player.playWhenReady,
                )
            }
            assertTrue(
                "Paused title must remain bounded",
                titleIsBounded(),
            )
            composeRule.runOnIdle {
                player.update {
                    setPlayWhenReady(
                        true,
                        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                    )
                }
            }
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.runOnIdle { assertTrue(player.playWhenReady) }
            assertEquals(
                false,
                titleIsBounded(),
            )
            composeRule.runOnIdle {
                Settings.Global.putFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    0f,
                )
                resolver.notifyChange(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                    null,
                )
            }
            assertTrue(titleIsBounded())
        } finally {
            composeRule.runOnIdle {
                Settings.Global.putString(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    original,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun lostConnectionDoesNotScrollARetainedPlayingTitle() {
        composeRule.runOnIdle {
            player.update {
                setPlayWhenReady(
                    true,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                )
            }
        }
        expanded = true
        show(PlaybackConnection.LOST)
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("playback_media_title")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().layoutInput.constraints.hasBoundedWidth)
    }

    @Test fun portraitArtworkIsSquareAndTransportModesHaveSeparateRows() {
        expanded = true
        show()
        val artwork = composeRule.onNodeWithTag("playback_artwork").getUnclippedBoundsInRoot()
        val transport = composeRule.onNodeWithTag("playback_transport").getUnclippedBoundsInRoot()
        val modes = composeRule.onNodeWithTag("playback_modes").getUnclippedBoundsInRoot()
        assertEquals(
            artwork.right - artwork.left,
            artwork.bottom - artwork.top,
        )
        val metadata = composeRule.onNodeWithTag("playback_metadata").getUnclippedBoundsInRoot()
        assertEquals(
            metadata.right - metadata.left,
            artwork.right - artwork.left,
        )
        assertEquals(
            80.dp,
            transport.bottom - transport.top,
        )
        assertTrue(modes.top >= transport.bottom)
    }

    private fun assertTransitionMetadataDoesNotCoverArtwork() {
        val artwork = composeRule.onAllNodesWithTag(
            "transition_artwork",
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes().singleOrNull()?.boundsInRoot ?: return
        val metadata = composeRule.onAllNodesWithTag(
            "transition_metadata",
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes().singleOrNull()?.boundsInRoot ?: return
        assertTrue(
            "Metadata $metadata overlaps artwork $artwork",
            metadata.right <= artwork.left || metadata.left >= artwork.right ||
                metadata.bottom <= artwork.top || metadata.top >= artwork.bottom,
        )
    }

    private fun show(
        connection: PlaybackConnection = PlaybackConnection.LOCAL,
        largeText: Boolean = false,
        deviceName: String = "TV",
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density.density,
                    if (largeText) 2f else 1f,
                ),
                LocalLayoutDirection provides
                    if (largeText) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NextPlayerTheme {
                    PlaybackOverlayContent(
                        player,
                        rememberPlaybackSnapshot(player),
                        PlaybackPresentation(
                            connection,
                            deviceName,
                        ),
                        expanded,
                        {
                            expanded = it
                        },
                        0.dp,
                        0.dp,
                        hidden,
                        remember { FocusRequester() },
                        onCompactHeightChanged = {},
                        onDismissPlayback = { dismissed++ },
                        onCommand = { commands += it },
                        onStop = {},
                        onWatchVideo = { watched++ },
                    )
                }
            }
        }
    }
    private fun text(id: Int) = composeRule.activity.getString(id)
}
