package com.graviton.feature.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.player.extensions.formatted
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** How many chevrons the seek feedback draws in its travel row. */
private const val CHEVRON_COUNT = 3

/** One acceleration step; the chevrons take this long to travel once. */
private const val CHEVRON_PERIOD_MS = 900

/**
 * A translucent Material 3 surface carrying the seek feedback: animated directional chevrons, the
 * signed seek amount and the timestamp the seek will land on.
 *
 * Black-on-white translucency is used instead of a themed container on purpose: the card floats
 * over video, where the theme's surface colours would sit at the mercy of whatever frame is behind
 * them. A hairline border keeps the card legible over a bright scene, and the surfaces stay small
 * so subtitles and faces are never covered.
 */
@Composable
fun SeekFeedbackCard(
    deltaMs: Long,
    targetPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SeekChevrons(
                forward = deltaMs >= 0,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = seekDeltaLabel(deltaMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = targetPositionMs.milliseconds.formatted(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * The moving arrows of the seek feedback. The row is mirrored for a rewind and each chevron fades
 * on its own phase, so the motion always reads as travelling in the direction the player is being
 * moved — the direction is legible even in a single frame.
 */
@Composable
fun SeekChevrons(
    forward: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconSize: Dp = 18.dp,
) {
    val transition = rememberInfiniteTransition(label = "SeekChevrons")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CHEVRON_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ChevronPhase",
    )

    Row(
        modifier = modifier.rotate(if (forward) 0f else 180f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(CHEVRON_COUNT) { index ->
            Icon(
                imageVector = NextIcons.Fast,
                // Decorative: the amount and the timestamp next to it carry the meaning.
                contentDescription = null,
                tint = tint.copy(alpha = chevronAlpha(phase, index)),
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = ((phase - 0.5f) * 5f).dp),
            )
        }
    }
}

/** A readable, localised seek amount: `+10 seconds` while seeking forward, `−10 seconds` back. */
@Composable
fun seekDeltaLabel(deltaMs: Long): String {
    val seconds = abs(deltaMs).milliseconds.inWholeSeconds
    return if (deltaMs >= 0) {
        stringResource(R.string.seek_seconds_forward, seconds)
    } else {
        stringResource(R.string.seek_seconds_backward, seconds)
    }
}

private fun chevronAlpha(phase: Float, index: Int): Float {
    val shifted = (phase + index * 0.2f) % 1f
    return (0.2f + 0.8f * (1f - shifted)).coerceIn(0.2f, 1f)
}
