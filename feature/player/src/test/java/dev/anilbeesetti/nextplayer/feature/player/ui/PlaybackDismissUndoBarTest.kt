package dev.anilbeesetti.nextplayer.feature.player.ui

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackDismissUndoBarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var restored = 0
    private var closed = 0

    @Test fun miniHeightPillHasIndependentUndoAndCloseActions() {
        show()
        val pill = rule.onNodeWithTag("playback_undo_pill").getUnclippedBoundsInRoot()
        assertEquals(
            64.dp,
            pill.bottom - pill.top,
        )
        rule.onNodeWithText(rule.activity.getString(R.string.undo)).performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.close)).performClick()
        assertEquals(
            1,
            restored,
        )
        assertEquals(
            1,
            closed,
        )
    }

    @Test fun largeRtlTextKeepsBothActionsWithinThePill() {
        show(largeRtl = true)
        val pill = rule.onNodeWithTag("playback_undo_pill").getUnclippedBoundsInRoot()
        for (action in listOf(
            rule.onNodeWithText(rule.activity.getString(R.string.undo)),
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.close)),
        )) {
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= pill.left && bounds.right <= pill.right)
            assertTrue(bounds.top >= pill.top && bounds.bottom <= pill.bottom)
        }
    }

    @Test fun exitingPillCannotSubmitUndoOrCloseAgain() {
        show(enabled = false)
        val undo = rule.onNodeWithText(rule.activity.getString(R.string.undo))
        val close = rule.onNodeWithContentDescription(rule.activity.getString(R.string.close))
        undo.assertIsNotEnabled()
        close.assertIsNotEnabled()
        undo.performSemanticsAction(SemanticsActions.OnClick) { it() }
        close.performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(
            0,
            restored,
        )
        assertEquals(
            0,
            closed,
        )
    }

    private fun show(largeRtl: Boolean = false, enabled: Boolean = true) {
        val deadline = SystemClock.elapsedRealtime() + 4_000
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density.density,
                    if (largeRtl) 2f else 1f,
                ),
                LocalLayoutDirection provides
                    if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NextPlayerTheme {
                    PlaybackDismissUndoBar(
                        deadline,
                        onUndo = {
                            restored++
                        },
                        onClose = { closed++ },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
