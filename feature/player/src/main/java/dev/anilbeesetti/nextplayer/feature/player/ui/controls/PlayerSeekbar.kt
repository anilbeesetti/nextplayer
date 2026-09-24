package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.Chapter
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls
import dev.anilbeesetti.nextplayer.feature.player.state.currentChapterIndex

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
internal fun PlayerSeekbar(
    modifier: Modifier = Modifier,
    position: Float,
    duration: Float,
    chapters: List<Chapter>,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var lastSeekChapterIndex by remember(chapters) { mutableStateOf<Int?>(null) }
    val onValueChange: (Float) -> Unit = { value ->
        val chapterIndex = chapters.currentChapterIndex(value.toLong())
        val previousChapterIndex = lastSeekChapterIndex ?: chapters.currentChapterIndex(position.toLong())
        if (chapterIndex != previousChapterIndex) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        lastSeekChapterIndex = chapterIndex
        onSeek(value)
    }
    val onValueChangeFinished = {
        lastSeekChapterIndex = null
        onSeekFinished()
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusModifier = modifier
        .fillMaxWidth()
        .onFocusChanged { isFocused = it.isFocused }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (LocalUseMaterialYouControls.current) {
            MaterialYouSlider(
                modifier = focusModifier,
                isFocused = isFocused,
                chapters = chapters,
                value = position,
                valueRange = 0f..(duration.takeIf { it > 0 } ?: 0f),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
            )
        } else {
            SimpleSlider(
                modifier = focusModifier,
                isFocused = isFocused,
                chapters = chapters,
                value = position,
                valueRange = 0f..(duration.takeIf { it > 0 } ?: 0f),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialYouSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    isFocused: Boolean = false,
    chapters: List<Chapter> = emptyList(),
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val trackHeight = 8.dp
    val thumbWidth = 4.dp
    val trackThumbGapWidth = 12.dp
    val focusedThumbWidth by animateDpAsState(if (isFocused) 10.dp else thumbWidth, label = "thumbWidth")
    val focusedThumbHeight by animateDpAsState(if (isFocused) 24.dp else 20.dp, label = "thumbHeight")

    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.size(24.dp),
        track = { sliderState ->
            val disabledAlpha = 0.4f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .chapterGaps(chapters, valueRange.endInclusive),
            ) {
                val min = sliderState.valueRange.start
                val max = sliderState.valueRange.endInclusive
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val playedFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)
                val playedPixels = size.width * playedFraction

                val endCornerRadius = size.height / 2f
                val insideCornerRadius = 2.dp.toPx()
                val gapHalf = trackThumbGapWidth.toPx() / 2f
                val leftEnd = (playedPixels - gapHalf).coerceIn(0f, size.width)
                val rightStart = (playedPixels + gapHalf).coerceIn(0f, size.width)

                // Inactive track left side
                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }

                // Inactive track right side
                if (rightStart < size.width) {
                    drawRoundedRect(
                        offset = Offset(rightStart, 0f),
                        size = Size(size.width - rightStart, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = insideCornerRadius,
                        endCornerRadius = endCornerRadius,
                    )
                }

                // Active track
                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor,
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }
            }
        },
        thumb = {
            Box(
                modifier = Modifier
                    .width(focusedThumbWidth)
                    .height(focusedThumbHeight)
                    .background(primaryColor, CircleShape)
                    .then(
                        if (isFocused) {
                            Modifier.border(2.dp, Color.White, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        },
    )
}

private fun DrawScope.drawRoundedRect(
    offset: Offset,
    size: Size,
    color: Color,
    startCornerRadius: Float,
    endCornerRadius: Float,
) {
    val startCorner = CornerRadius(startCornerRadius, startCornerRadius)
    val endCorner = CornerRadius(endCornerRadius, endCornerRadius)
    val track = RoundRect(
        rect = Rect(Offset(offset.x, 0f), size = Size(size.width, size.height)),
        topLeft = startCorner,
        topRight = endCorner,
        bottomRight = endCorner,
        bottomLeft = startCorner,
    )
    drawPath(
        path = Path().apply {
            addRoundRect(track)
        },
        color = color,
    )
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    isFocused: Boolean = false,
    chapters: List<Chapter> = emptyList(),
) {
    val thumbSize by animateDpAsState(if (isFocused) 22.dp else 16.dp, label = "thumbSize")
    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.height(24.dp),
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White)
                    .then(
                        if (isFocused) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .chapterGaps(chapters, valueRange.endInclusive)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color.White.copy(0.5f)),
            ) {
                if (valueRange.endInclusive > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value / valueRange.endInclusive)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        },
    )
}

@OptIn(UnstableApi::class)
private fun Modifier.chapterGaps(chapters: List<Chapter>, duration: Float): Modifier = drawWithCache {
    val gaps = Path()
    if (duration > 0f) {
        val halfGap = 1.5.dp.toPx()
        chapters.forEach { chapter ->
            if (chapter.startTimeMs > 0 && chapter.startTimeMs < duration) {
                val x = size.width * (chapter.startTimeMs / duration)
                gaps.addRect(Rect(x - halfGap, 0f, x + halfGap, size.height))
            }
        }
    }
    onDrawWithContent {
        clipPath(gaps, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
    }
}
