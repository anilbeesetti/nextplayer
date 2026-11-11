package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.toTypeface

private const val SECONDARY_TEXT_SCALE = 0.85f
private const val SECONDARY_BACKGROUND_ALPHA = 0.6f

/** Draws the text of the secondary subtitle at the top of the screen. */
@OptIn(UnstableApi::class)
@Composable
fun SecondarySubtitleView(
    cues: List<Cue>,
    configuration: SubtitleConfiguration,
    modifier: Modifier = Modifier,
) {
    val text = cues
        .mapNotNull { cue -> cue.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
        .joinToString(separator = "\n")
    if (text.isEmpty()) return

    val fontFamily = remember(configuration.font) { FontFamily(configuration.font.toTypeface()) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = text,
            modifier = Modifier.background(
                color = if (configuration.showBackground) {
                    Color.Black.copy(alpha = SECONDARY_BACKGROUND_ALPHA)
                } else {
                    Color.Transparent
                },
            ),
            color = Color.White,
            fontSize = (configuration.textSize * SECONDARY_TEXT_SCALE).sp,
            fontWeight = if (configuration.textBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(
                        x = 2f,
                        y = 2f,
                    ),
                    blurRadius = 4f,
                ),
            ),
        )
    }
}
