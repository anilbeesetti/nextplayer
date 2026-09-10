package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = enabled,
    onClick = onClick,
)

/**
 * Sizes the content to the visible video bounds rather than the entire player.
 *
 * Uses the same measurement behavior as [androidx.media3.ui.compose.modifiers.resizeWithContentScale],
 * but clamps each axis to the player bounds so overlays match only the portion of the video visible on screen.
 */
internal fun Modifier.videoContentFrame(contentScale: ContentScale, videoSizeDp: Size?): Modifier = this
    .fillMaxSize()
    .wrapContentSize()
    .layout { measurable, constraints ->
        val frameSize = calculateVisibleVideoSize(
            videoSize = videoSizeDp?.let { Size(Dp(it.width).toPx(), Dp(it.height).toPx()) },
            containerSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
            contentScale = contentScale,
        )
        val placeable = measurable.measure(
            constraints.copy(
                maxWidth = frameSize.width.roundToInt(),
                maxHeight = frameSize.height.roundToInt(),
            ),
        )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * Falls back to [containerSize] until the video size is known,
 * so that overlays do not jump when the first frame arrives.
 */
private fun calculateVisibleVideoSize(videoSize: Size?, containerSize: Size, contentScale: ContentScale): Size {
    if (videoSize == null || videoSize.width <= 0f || videoSize.height <= 0f) return containerSize

    val scaleFactor = contentScale.computeScaleFactor(videoSize, containerSize)
    return Size(
        width = min(videoSize.width * scaleFactor.scaleX, containerSize.width),
        height = min(videoSize.height * scaleFactor.scaleY, containerSize.height),
    )
}
