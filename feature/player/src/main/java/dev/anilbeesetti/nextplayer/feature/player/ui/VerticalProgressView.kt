package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.IntRange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

private const val NORMAL_MAX_PERCENTAGE = 100

/**
 * Vertical progress indicator for volume/brightness.
 *
 * @param value Current value (0 to maxValue)
 * @param maxValue Maximum value (100 for normal, 200 for boosted volume)
 * @param icon Icon to display at the bottom
 * @param boostColor Optional color for the boost portion (above 100%)
 */
@Composable
internal fun VerticalProgressView(
    useMaterialYouControls: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    icon: Painter,
    @IntRange(
        from = 0,
        to = 200,
    ) value: Int,
    maxValue: Int = NORMAL_MAX_PERCENTAGE,
    boostColor: Color = Color(color = 0xFFFC6E6E),
) {
    val normalizedValue = value.coerceIn(
        minimumValue = 0,
        maximumValue = maxValue,
    )
    val fillFraction = normalizedValue.toFloat() / maxValue.toFloat()
    val isBoostActive = maxValue > NORMAL_MAX_PERCENTAGE && value > NORMAL_MAX_PERCENTAGE

    val colors = MaterialTheme.colorScheme
    val backgroundColor = if (useMaterialYouControls) {
        colors.background
    } else {
        Color.Black.copy(alpha = 0.5f)
    }
    val contentColor = if (useMaterialYouControls) colors.onBackground else Color.White
    val trackColor = if (useMaterialYouControls) {
        colors.surfaceVariant
    } else {
        Color.White.copy(alpha = 0.5f)
    }
    val trackWidth = if (useMaterialYouControls) width else 4.dp
    val trackShape = if (useMaterialYouControls) MaterialTheme.shapes.medium else CircleShape
    val fillColor = when {
        isBoostActive -> boostColor
        useMaterialYouControls -> colors.primary
        else -> Color.White
    }

    Column(
        modifier = modifier
            .heightIn(max = 220.dp)
            .clip(shape = CircleShape)
            .background(backgroundColor)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(size = width),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = normalizedValue.toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = contentColor,
                ),
                autoSize = TextAutoSize.StepBased(
                    maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                ),
            )
        }
        Box(
            modifier = Modifier
                .weight(weight = 1f)
                .width(trackWidth)
                .clip(trackShape)
                .background(trackColor),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(trackWidth)
                    .fillMaxHeight(fraction = fillFraction)
                    .background(fillColor),
            )
        }
        Box(
            modifier = Modifier.size(size = width),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

@Preview
@Composable
private fun SimpleVerticalProgressPreview() {
    NextPlayerTheme {
        VerticalProgressView(
            useMaterialYouControls = false,
            value = 50,
            icon = painterResource(R.drawable.ic_volume),
        )
    }
}

@Preview
@Composable
private fun VerticalProgressBoostPreview() {
    NextPlayerTheme {
        VerticalProgressView(
            useMaterialYouControls = true,
            value = 150,
            maxValue = 200,
            icon = painterResource(R.drawable.ic_volume),
        )
    }
}
