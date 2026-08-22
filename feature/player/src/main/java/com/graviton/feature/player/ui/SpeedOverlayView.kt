package com.graviton.feature.player.ui
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun BoxScope.SpeedOverlayView(
    modifier: Modifier = Modifier,
    speed: Float,
    isLongPressActive: Boolean = false,
) {
    var showOverlay by remember { mutableStateOf(false) }
    var isFirstLaunch by remember { mutableStateOf(true) }

    LaunchedEffect(speed, isLongPressActive) {
        if (isLongPressActive) {
            showOverlay = true
        } else {
            if (isFirstLaunch) {
                isFirstLaunch = false
            } else {
                showOverlay = true
                delay(1500)
                showOverlay = false
            }
        }
    }

    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f, animationSpec = tween(300)),
        modifier = modifier.align(Alignment.TopCenter).padding(top = 48.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val formattedSpeed = if (speed % 1.0f == 0.0f) "${speed.toInt()} \u00d7  \u25b6\u25b6" else "${speed} \u00d7  \u25b6\u25b6"
            Text(
                text = formattedSpeed,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
