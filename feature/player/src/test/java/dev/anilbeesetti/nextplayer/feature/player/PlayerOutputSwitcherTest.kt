package dev.anilbeesetti.nextplayer.feature.player

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerOutputSwitcherTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val player = MiniPlayerTestPlayer()
    private val actions = mutableListOf<PlayerAction>()

    @After fun tearDown() = rule.runOnIdle { player.release() }

    @Test fun outputSheetSurvivesHiddenControlsAndBackReturnsToThePlayer() {
        shadowOf(rule.activity.getSystemService(AudioManager::class.java)).setOutputDevices(
            listOf(
                AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    .build(),
            ),
        )
        rule.setContent {
            NextPlayerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    MediaPlayerContent(
                        player,
                        PlayerUiState(),
                        actions::add,
                    )
                }
            }
        }
        rule.onNodeWithTag("player_output_action").performClick()
        rule.onNodeWithTag("playback_outputs").assertIsDisplayed()
        rule.onNodeWithTag("playback_output_devices").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.this_phone)).assertIsSelected()
        val moreDevices = rule.onNodeWithText(rule.activity.getString(R.string.more_devices))
        moreDevices.assertIsDisplayed()
        val semantics = moreDevices.fetchSemanticsNode().config
        assertEquals(
            Role.Button,
            semantics[SemanticsProperties.Role],
        )
        assertFalse(semantics.contains(SemanticsProperties.Selected))
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("playback_outputs").assertDoesNotExist()
        rule.onNodeWithTag("player_output_action").assertIsDisplayed()
        rule.runOnIdle { assertFalse(actions.contains(PlayerAction.NavigateUp)) }
    }
}
