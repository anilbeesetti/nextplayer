package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.PlaybackArtwork
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PlaybackArtworkFitTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun wideArtworkKeepsItsAspectRatioInsideTheSquare() {
        rule.setContent {
            NextPlayerTheme {
                PlaybackArtwork(
                    snapshot,
                    Modifier.size(100.dp).testTag("artwork"),
                    painter = wideRedPainter,
                )
            }
        }
        val pixels = rule.onNodeWithTag("artwork").captureToImage().toPixelMap()
        val center = pixels.width / 2
        assertEquals(
            Color.Red,
            pixels[center, pixels.height / 2],
        )
        assertNotEquals(
            Color.Red,
            pixels[center, pixels.height / 8],
        )
    }

    private val wideRedPainter = object : Painter() {
        override val intrinsicSize = Size(
            200f,
            100f,
        )

        override fun DrawScope.onDraw() = drawRect(Color.Red)
    }

    private val snapshot = PlaybackSnapshot(
        item = null,
        metadata = MediaMetadata.EMPTY,
        playbackState = Player.STATE_READY,
        playWhenReady = true,
        deviceInfo = DeviceInfo.UNKNOWN,
        duration = 0,
        position = 0,
        commands = Player.Commands.EMPTY,
        deviceVolume = 0,
        volume = 1f,
        shuffle = false,
        repeatMode = Player.REPEAT_MODE_OFF,
    )
}
