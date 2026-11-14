package dev.anilbeesetti.nextplayer.settings.screens.developer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeveloperPreferencesScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun inlineChoicesUpdateEachStyleWithoutOpeningADialog() {
        var preferences by mutableStateOf(PlayerPreferences())
        rule.setContent {
            NextPlayerTheme {
                DeveloperPreferencesScreenContent(preferences) {
                    preferences = when (it) {
                        is DeveloperPreferencesEvent.ControlsArtwork ->
                            preferences.copy(controlsArtworkStyle = it.style)

                        is DeveloperPreferencesEvent.QueueArtwork ->
                            preferences.copy(queueArtworkStyle = it.style)

                        DeveloperPreferencesEvent.NavigateUp -> preferences
                    }
                }
            }
        }
        rule.onNodeWithTag("controls_artwork_style_VIDEO").assertIsSelected()
        rule.onNodeWithTag("queue_artwork_style_VIDEO").assertIsSelected()
        rule.onNodeWithTag("controls_artwork_style_AUDIO").performClick().assertIsSelected()
        rule.onNodeWithTag("controls_artwork_style_VIDEO").assertIsNotSelected()
        rule.onNodeWithTag("queue_artwork_style_VIDEO").assertIsSelected()
        rule.onNodeWithTag("queue_artwork_style_AUDIO").performClick().assertIsSelected()
        rule.onNodeWithTag("controls_artwork_style_VIDEO").performClick().assertIsSelected()
        rule.onNodeWithTag("queue_artwork_style_AUDIO").assertIsSelected()
    }
}
