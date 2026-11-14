package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36])
class PlaybackMotionTest {
    private val durationScale = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }

    @get:Rule val composeRule = createComposeRule(effectContext = durationScale)

    @Test fun followsComposesAnimationScaleIncludingChangesWhileMounted() {
        var enabled = false
        composeRule.setContent { enabled = playbackAnimationsEnabled() }
        composeRule.runOnIdle {
            assertTrue(enabled)
            durationScale.scaleFactor = 0f
        }
        composeRule.runOnIdle {
            assertFalse(enabled)
            durationScale.scaleFactor = .5f
        }
        composeRule.runOnIdle { assertTrue(enabled) }
    }
}
