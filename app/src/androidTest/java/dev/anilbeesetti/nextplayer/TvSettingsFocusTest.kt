package dev.anilbeesetti.nextplayer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.ui.R
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvSettingsFocusTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun openSettings() {
        assumeTrue(composeRule.activity.isTelevision)
        composeRule.onNodeWithContentDescription(text(R.string.settings)).performClick()
        focused(R.string.appearance_name)
    }

    @Test
    fun returningFromSettingsRestoresTheSelectedRow() {
        press(Key.DirectionDown)
        focused(R.string.media_library)
        press(Key.DirectionCenter)
        focused(R.string.mark_last_played_media)
        press(Key.Back)
        focused(R.string.media_library)
        press(Key.DirectionDown)
        focused(R.string.player_name)
    }

    @Test
    fun returningFromNestedSettingsRestoresTheSelectedRow() {
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        focused(R.string.mark_last_played_media)
        press(Key.DirectionDown)
        focused(R.string.manage_folders)
        press(Key.DirectionDown)
        focused(R.string.thumbnail_generation)
        press(Key.DirectionCenter)
        composeRule.onNodeWithContentDescription(text(R.string.navigate_up)).assertIsDisplayed()
        press(Key.Back)
        focused(R.string.thumbnail_generation)
        press(Key.Back)
        focused(R.string.media_library)
    }

    @Test
    fun everySettingsPageStartsInContentAndReturnsToItsRow() {
        val rows = listOf(
            R.string.appearance_name,
            R.string.media_library,
            R.string.player_name,
            R.string.gestures_name,
            R.string.audio,
            R.string.subtitle,
            R.string.general_name,
            R.string.about_name,
        )
        rows.forEachIndexed { index, row ->
            focused(row)
            press(Key.DirectionCenter)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(
                    isFocused() and hasClickAction() and !hasContentDescription(text(R.string.navigate_up)),
                ).fetchSemanticsNodes().size == 1
            }
            press(Key.Back)
            focused(row)
            if (index < rows.lastIndex) press(Key.DirectionDown)
        }
    }

    @Test
    fun activityRecreationRestoresTheFocusedSettingsRow() {
        press(Key.DirectionDown)
        focused(R.string.media_library)
        composeRule.activityRule.scenario.recreate()
        focused(R.string.media_library)
    }

    private fun text(id: Int) = composeRule.activity.getString(id)

    private fun focused(id: Int) {
        try {
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText(text(id)) and isFocused())
                    .fetchSemanticsNodes().size == 1
            }
        } catch (failure: Throwable) {
            throw AssertionError("Expected focus on ${text(id)}\n${composeRule.onRoot().printToString()}", failure)
        }
    }

    private fun press(key: Key) {
        if (key == Key.Back) {
            pressBack()
        } else {
            composeRule.onRoot().performKeyInput { pressKey(key) }
        }
        composeRule.waitForIdle()
    }
}
