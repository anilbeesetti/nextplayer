package com.graviton.feature.player.ui

import androidx.annotation.IntRange
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R
import com.graviton.core.ui.theme.GravitonTheme

private const val NORMAL_MAX_PERCENTAGE = 100

@Composable
fun VerticalProgressView(
    modifier: Modifier = Modifier,
    icon: Painter,
    @IntRange(from = 0, to = 200) value: Int,
    maxValue: Int = NORMAL_MAX_PERCENTAGE,
    boostColor: Color = Color(0xFFFC6E6E),
) {
    val normalizedValue = value.coerceIn(0, maxValue)
    val fillFraction = normalizedValue.toFloat() / maxValue.toFloat()
    val isBoostActive = maxValue > NORMAL_MAX_PERCENTAGE && value > NORMAL_MAX_PERCENTAGE

    val textStr = normalizedValue.toString()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = textStr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 48.dp),
            )

            VerticalSlider(
                value = normalizedValue,
                range = 0..maxValue,
                overflowValue = if (isBoostActive) value - NORMAL_MAX_PERCENTAGE else null,
                overflowRange = if (isBoostActive) 0..(maxValue - NORMAL_MAX_PERCENTAGE) else null,
            )

            Icon(
                painter = icon,
                contentDescription = stringResource(com.graviton.core.ui.R.string.value_slider_icon_description),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun VerticalSlider(
    value: Int,
    range: ClosedRange<Int>,
    modifier: Modifier = Modifier,
    overflowValue: Int? = null,
    overflowRange: ClosedRange<Int>? = null,
    colorStart: Color = Color(0xFFD0BCFF), // primaryContainer equivalent approx
    colorEnd: Color = Color(0xFF6750A4),   // primary equivalent approx
) {
    val coercedValue = value.coerceIn(range)
    val start = range.start.toFloat()
    val end = range.endInclusive.toFloat()
    val percentage = ((coercedValue - start) / (end - start)).coerceIn(0f, 1f)

    val colorStartValue = MaterialTheme.colorScheme.primaryContainer
    val colorEndValue = MaterialTheme.colorScheme.primary
    val gradientBrush = remember(colorStartValue, colorEndValue) {
        Brush.verticalGradient(listOf(colorStartValue, colorEndValue))
    }

    Box(
        modifier = modifier
            .height(130.dp)
            .width(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val targetHeight by animateFloatAsState(
            targetValue = percentage,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
            label = "vsliderheight",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(targetHeight.coerceAtLeast(0.05f))
                .clip(RoundedCornerShape(18.dp))
                .background(gradientBrush),
        )
        if (overflowRange != null && overflowValue != null) {
            val overflowStart = overflowRange.start.toFloat()
            val overflowEnd = overflowRange.endInclusive.toFloat()
            val overflowPercentage = ((overflowValue - overflowStart) / (overflowEnd - overflowStart)).coerceIn(0f, 1f)
            val overflowHeight by animateFloatAsState(
                targetValue = overflowPercentage,
                label = "vslideroverflowheight",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(overflowHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
            )
        }
    }
}

@Preview
@Composable
private fun VerticalProgressPreview() {
    GravitonTheme {
        VerticalProgressView(
            value = 50,
            icon = painterResource(R.drawable.ic_volume),
        )
    }
}

@Preview
@Composable
private fun VerticalProgressBoostPreview() {
    GravitonTheme {
        VerticalProgressView(
            value = 150,
            maxValue = 200,
            icon = painterResource(R.drawable.ic_volume),
        )
    }
}
