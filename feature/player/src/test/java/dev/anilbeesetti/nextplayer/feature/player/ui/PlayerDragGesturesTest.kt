package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.feature.player.extensions.detectCustomHorizontalDragGestures
import dev.anilbeesetti.nextplayer.feature.player.extensions.detectCustomVerticalDragGestures
import dev.anilbeesetti.nextplayer.feature.player.extensions.detectSubtitleDragGestures
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
class PlayerDragGesturesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var subtitleStarts = 0
    private var subtitleEnds = 0
    private var subtitleCancels = 0
    private var subtitlePressed = false
    private var horizontalStarts = 0
    private var verticalStarts = 0
    private var dragUpdates = 0
    private var dragEnds = 0
    private var consumedMoves = 0

    @Test
    fun edgeSwipesRemainIgnoredAfterMovingIntoThePlayer() {
        setGestureContent()
        val player = composeRule.onNodeWithTag("gestures")
        for (edge in 0..3) {
            player.performTouchInput {
                val start = when (edge) {
                    0 -> Offset(
                        1f,
                        centerY,
                    )

                    1 -> Offset(
                        width - 1f,
                        centerY,
                    )

                    2 -> Offset(
                        centerX,
                        1f,
                    )

                    else -> Offset(
                        centerX,
                        height - 1f,
                    )
                }
                swipe(
                    start,
                    center,
                    durationMillis = 200,
                )
            }
        }
        composeRule.runOnIdle {
            assertEquals(
                0,
                horizontalStarts,
            )
            assertEquals(
                0,
                verticalStarts,
            )
            assertEquals(
                0,
                dragUpdates,
            )
            assertEquals(
                0,
                dragEnds,
            )
            assertEquals(
                0,
                consumedMoves,
            )
        }

        // A rejected stream must not prevent the next ordinary seek gesture.
        player.performTouchInput {
            swipe(
                Offset(
                    width * 0.25f,
                    centerY,
                ),
                Offset(
                    width * 0.75f,
                    centerY,
                ),
            )
        }
        composeRule.runOnIdle {
            assertEquals(
                1,
                horizontalStarts,
            )
            assertEquals(
                1,
                dragEnds,
            )
            assertTrue(dragUpdates > 0)
        }
    }

    @Test
    fun interiorSeekAndBothVerticalGesturesCanContinueIntoAnEdge() {
        setGestureContent()
        val player = composeRule.onNodeWithTag("gestures")
        player.performTouchInput {
            swipe(
                center,
                Offset(
                    width - 1f,
                    centerY,
                ),
            )
        }
        for (fraction in listOf(
            0.25f,
            0.75f,
        )) {
            player.performTouchInput {
                swipe(
                    Offset(
                        width * fraction,
                        centerY,
                    ),
                    Offset(
                        width * fraction,
                        1f,
                    ),
                )
            }
        }
        composeRule.runOnIdle {
            assertEquals(
                1,
                horizontalStarts,
            )
            assertEquals(
                2,
                verticalStarts,
            )
            assertEquals(
                3,
                dragEnds,
            )
            assertTrue(dragUpdates > 0)
            assertTrue(consumedMoves > 0)
        }
    }

    @Test
    fun insetBoundsUsePhysicalEdgesInEitherLayoutDirection() {
        val insets = WindowInsets(
            left = 24,
            top = 40,
            right = 28,
            bottom = 48,
        )
        val size = IntSize(
            300,
            200,
        )
        for (direction in LayoutDirection.entries) {
            fun allowed(x: Float, y: Float) = insets.isPlayerGestureStart(
                Offset(
                    x,
                    y,
                ),
                size,
                Density(2f),
                direction,
            )
            assertFalse(
                allowed(
                    23f,
                    100f,
                ),
            )
            assertFalse(
                allowed(
                    272f,
                    100f,
                ),
            )
            assertFalse(
                allowed(
                    150f,
                    39f,
                ),
            )
            assertFalse(
                allowed(
                    150f,
                    152f,
                ),
            )
            assertTrue(
                allowed(
                    24f,
                    40f,
                ),
            )
            assertTrue(
                allowed(
                    271f,
                    151f,
                ),
            )
        }
        assertTrue(
            WindowInsets(
                0,
            ).isPlayerGestureStart(
                Offset.Zero,
                size,
                Density(1f),
                LayoutDirection.Ltr,
            ),
        )
    }

    @Test
    fun subtitleVerticalDragWinsButHorizontalSwipeSeeks() {
        setGestureContent(subtitles = true)
        val player = composeRule.onNodeWithTag("gestures")
        player.performTouchInput {
            swipe(
                center,
                Offset(
                    centerX,
                    centerY - 100f,
                ),
            )
        }
        composeRule.runOnIdle {
            assertEquals(
                1,
                subtitleStarts,
            )
            assertEquals(
                1,
                subtitleEnds,
            )
            assertEquals(
                0,
                verticalStarts,
            )
            assertFalse(subtitlePressed)
        }
        player.performTouchInput {
            swipe(
                center,
                Offset(
                    centerX + 100f,
                    centerY,
                ),
            )
        }
        composeRule.runOnIdle {
            assertEquals(
                1,
                subtitleStarts,
            )
            assertEquals(
                1,
                horizontalStarts,
            )
            assertFalse(subtitlePressed)
        }
    }

    @Test
    fun subtitleCancelDoesNotSave() {
        setGestureContent(subtitles = true)
        composeRule.onNodeWithTag("gestures").performTouchInput {
            down(center)
            moveTo(
                Offset(
                    centerX,
                    centerY - 100f,
                ),
            )
            cancel()
        }
        composeRule.runOnIdle {
            assertEquals(
                1,
                subtitleStarts,
            )
            assertEquals(
                0,
                subtitleEnds,
            )
            assertEquals(
                1,
                subtitleCancels,
            )
            assertFalse(subtitlePressed)
        }
    }

    @Test
    fun secondPointerDoesNotStartOrSaveSubtitleDrag() {
        setGestureContent(subtitles = true)
        val player = composeRule.onNodeWithTag("gestures")
        player.performTouchInput {
            down(center)
            down(
                1,
                center + Offset(
                    50f,
                    0f,
                ),
            )
            moveBy(
                Offset(
                    0f,
                    -100f,
                ),
            )
            up(1)
            up()
        }
        composeRule.runOnIdle {
            assertEquals(
                0,
                subtitleStarts,
            )
            assertEquals(
                0,
                subtitleEnds,
            )
            assertFalse(subtitlePressed)
        }
        player.performTouchInput {
            down(center)
            moveTo(
                Offset(
                    centerX,
                    centerY - 100f,
                ),
            )
            down(
                1,
                center + Offset(
                    50f,
                    0f,
                ),
            )
            moveBy(
                Offset(
                    0f,
                    -20f,
                ),
            )
            up(1)
            up()
        }
        composeRule.runOnIdle {
            assertEquals(
                1,
                subtitleStarts,
            )
            assertEquals(
                0,
                subtitleEnds,
            )
            assertEquals(
                1,
                subtitleCancels,
            )
            assertFalse(subtitlePressed)
        }
    }

    private fun setGestureContent(subtitles: Boolean = false) {
        composeRule.setContent {
            val insets = remember {
                WindowInsets(
                    left = 24,
                    top = 40,
                    right = 28,
                    bottom = 48,
                )
            }
            val density = LocalDensity.current
            val direction = LocalLayoutDirection.current
            Box(
                Modifier
                    .size(300.dp)
                    .testTag("gestures")
                    .pointerInput(subtitles) {
                        if (!subtitles) return@pointerInput
                        detectSubtitleDragGestures(
                            shouldStartGesture = { true },
                            onPressChange = { subtitlePressed = it },
                            onDragStart = { subtitleStarts++ },
                            onDrag = {},
                            onDragEnd = { subtitleEnds++ },
                            onDragCancel = { subtitleCancels++ },
                        )
                    }
                    .pointerInput(Unit) {
                        detectCustomHorizontalDragGestures(
                            shouldStartGesture = {
                                insets.isPlayerGestureStart(
                                    it,
                                    size,
                                    density,
                                    direction,
                                )
                            },
                            onDragStart = { horizontalStarts++ },
                            onHorizontalDrag = {
                                    _,
                                    _,
                                ->
                                dragUpdates++
                            },
                            onDragEnd = { dragEnds++ },
                        )
                    }
                    .pointerInput(Unit) {
                        detectCustomVerticalDragGestures(
                            shouldStartGesture = {
                                insets.isPlayerGestureStart(
                                    it,
                                    size,
                                    density,
                                    direction,
                                )
                            },
                            onDragStart = { verticalStarts++ },
                            onVerticalDrag = {
                                    _,
                                    _,
                                ->
                                dragUpdates++
                            },
                            onDragEnd = { dragEnds++ },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                consumedMoves +=
                                    event.changes.count {
                                        it.positionChangedIgnoreConsumed() &&
                                            it.isConsumed
                                    }
                            }
                        }
                    },
            )
        }
    }
}
