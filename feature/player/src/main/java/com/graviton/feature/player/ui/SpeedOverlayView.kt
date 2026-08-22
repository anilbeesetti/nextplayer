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

@Composable
fun BoxScope.SpeedOverlayView(
    modifier: Modifier = Modifier,
    speed: Float,
    isLongPressActive: Boolean = false,
) {
    var showOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(isLongPressActive, speed) {
        showOverlay = isLongPressActive
    }

    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.9f, animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
        modifier = modifier.align(Alignment.TopCenter).padding(top = 48.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(androidx.compose.ui.graphics.Color(0x99000000))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val formattedSpeed = if (speed % 1.0f == 0.0f) "${speed.toInt()}x Speed" else "${speed}x Speed"
            Text(
                text = formattedSpeed,
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}
