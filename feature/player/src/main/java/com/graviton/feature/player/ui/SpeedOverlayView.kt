package com.graviton.feature.player.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.graviton.core.ui.theme.GravitonTheme
import com.graviton.feature.player.state.HoldSpeedGesture

/**
 * Compact floating hold-speed indicator from mpvRex's CompactSpeedIndicator.
 *
 * Shown only while the finger is held. Speed text jumps immediately when the
 * hold-swipe selects a new preset (for example `2` + `×`).
 */
@Composable
fun BoxScope.SpeedOverlayView(
    modifier: Modifier = Modifier,
    speed: Float,
    isLongPressActive: Boolean = false,
    controlsVisible: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val topMargin = when {
        controlsVisible && isPortrait -> 104.dp
        controlsVisible -> 68.dp
        isPortrait -> 64.dp
        else -> 32.dp
    }
    val speedString = remember(speed) { HoldSpeedGesture.formatOverlaySpeed(speed) }

    AnimatedVisibility(
        visible = isLongPressActive,
        enter = fadeIn(tween(durationMillis = 100, easing = LinearOutSlowInEasing)),
        exit = fadeOut(tween(durationMillis = 300, easing = FastOutSlowInEasing)),
        modifier = modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = topMargin),
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = stringResource(com.graviton.core.ui.R.string.current_speed_description),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                AnimatedContent(
                    targetState = speedString,
                    transitionSpec = {
                        (
                            fadeIn(animationSpec = tween(100)) +
                                scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                ) +
                                slideInVertically(
                                    initialOffsetY = { it / 3 },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                )
                            ).togetherWith(
                            fadeOut(animationSpec = tween(100)) +
                                scaleOut(targetScale = 1.1f, animationSpec = tween(100)) +
                                slideOutVertically(
                                    targetOffsetY = { -it / 3 },
                                    animationSpec = tween(100),
                                ),
                        ).using(SizeTransform(clip = false))
                    },
                    label = "SpeedJumpAnimation",
                    modifier = Modifier.padding(start = 4.dp),
                ) { targetSpeed ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = targetSpeed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "×",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 1.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SpeedOverlayViewPreview() {
    GravitonTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            SpeedOverlayView(
                speed = 2.0f,
                isLongPressActive = true,
            )
        }
    }
}
