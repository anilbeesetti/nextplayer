package dev.anilbeesetti.nextplayer.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackContrastTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun playbackContentAndInactiveTrackRemainLegibleAcrossThemes() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var black by mutableStateOf(false)
        lateinit var colors: ColorScheme
        composeRule.setContent {
            NextPlayerTheme(
                darkTheme = dark,
                dynamicColor = dynamic,
                highContrastDarkTheme = black,
            ) {
                colors = MaterialTheme.colorScheme
            }
        }
        for (darkValue in listOf(
            false,
            true,
        )) {
            for (dynamicValue in listOf(
                false,
                true,
            )) {
                for (blackValue in listOf(
                    false,
                    true,
                )) {
                    composeRule.runOnIdle {
                        dark = darkValue
                        dynamic = dynamicValue
                        black = blackValue
                    }
                    composeRule.runOnIdle {
                        val theme = "dark=$dark dynamic=$dynamic black=$black"
                        for ((foreground, background) in listOf(
                            colors.onPrimaryContainer to colors.primaryContainer,
                            colors.primary to colors.onPrimary,
                            colors.onTertiaryContainer to colors.tertiaryContainer,
                            colors.onSurface to colors.surfaceContainerHigh,
                            colors.onSecondaryContainer to colors.secondaryContainer,
                        )) {
                            assertTrue(
                                "Text/icon contrast: $theme",
                                contrast(
                                    foreground,
                                    background,
                                ) >= 4.5f,
                            )
                        }
                        val track = playbackInactiveTrackColor(colors.onPrimaryContainer)
                        assertTrue(
                            "Inactive seek track: $theme",
                            contrast(
                                track,
                                colors.primaryContainer,
                            ) >= 3f,
                        )
                    }
                }
            }
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val front = foreground.compositeOver(background).luminance()
        val back = background.luminance()
        return (
            max(
                front,
                back,
            ) + .05f
            ) / (
            min(
                front,
                back,
            ) + .05f
            )
    }
}
