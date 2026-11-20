package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerticalProgressViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun disabledMaterialYouUsesThinWhiteTrack() {
        val useMaterialYouControls = mutableStateOf(true)
        val primary = Color.Cyan
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = primary)) {
                Box {
                    VerticalProgressView(
                        modifier = Modifier.height(200.dp),
                        useMaterialYouControls = useMaterialYouControls.value,
                        value = 100,
                        icon = painterResource(R.drawable.ic_volume),
                    )
                }
            }
        }

        val density = composeRule.density
        val trackCenterX = with(density) { 24.dp.roundToPx() }
        val trackOuterX = with(density) { 16.dp.roundToPx() }
        val trackCenterY = with(density) { 100.dp.roundToPx() }
        val material = composeRule.onRoot().captureToImage().toPixelMap()
        assertEquals(
            primary,
            material[trackCenterX, trackCenterY],
        )
        assertEquals(
            primary,
            material[trackOuterX, trackCenterY],
        )

        composeRule.runOnIdle { useMaterialYouControls.value = false }

        val simple = composeRule.onRoot().captureToImage().toPixelMap()
        assertEquals(
            Color.White,
            simple[trackCenterX, trackCenterY],
        )
        assertNotEquals(
            Color.White,
            simple[trackOuterX, trackCenterY],
        )
    }
}
