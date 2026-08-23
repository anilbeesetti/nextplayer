package com.graviton.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R

/**
 * The transient 2x interaction from mpvRex, adapted to Graviton's existing long-press gesture.
 * Visibility is driven directly by the gesture state (rather than a second timer/state machine),
 * so it cannot remain on screen after the gesture or after controls are hidden.
 */
@Composable
fun BoxScope.SpeedOverlayView(
    modifier: Modifier = Modifier,
    speed: Float,
    isLongPressActive: Boolean = false,
) {
    AnimatedVisibility(
        visible = isLongPressActive,
        enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.86f, animationSpec = tween(180)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.86f, animationSpec = tween(120)),
        modifier = modifier.align(Alignment.Center),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fast),
                contentDescription = null,
                tint = Color.White,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = speed,
                    transitionSpec = {
                        (fadeIn(tween(90)) + scaleIn(initialScale = 0.9f, animationSpec = tween(90))) togetherWith
                            (fadeOut(tween(70)) + scaleOut(targetScale = 0.9f, animationSpec = tween(70))) using
                            SizeTransform(clip = false)
                    },
                    label = "playback speed",
                ) { targetSpeed ->
                    Text(
                        text = formatSpeed(targetSpeed),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
                Text(
                    text = "hold to adjust",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "%.1fx".format(speed)
