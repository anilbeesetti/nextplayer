package com.graviton.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.graviton.feature.player.state.LongPressSeekState

/**
 * The hold-to-seek overlay.
 *
 * It sits on the side of the video the user is holding, so the feedback never lands under the
 * finger that is driving it. While the hold is live the card shows the amount that has already been
 * seeked and the timestamp the player is heading towards; both keep updating as the hold
 * accelerates ([LongPressSeekState]). On release the card fades out with the final values still on
 * screen, because the state keeps them instead of clearing them.
 */
@Composable
fun LongPressSeekIndicator(modifier: Modifier = Modifier, longPressSeekState: LongPressSeekState) {
    val direction = longPressSeekState.direction

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = longPressSeekState.isActive && direction != 0,
            modifier = Modifier.align(
                if (direction > 0) Alignment.CenterEnd else Alignment.CenterStart,
            ),
            enter = fadeIn(animationSpec = tween(120)) + scaleIn(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                initialScale = 0.92f,
            ),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                targetScale = 0.96f,
            ),
        ) {
            SeekFeedbackCard(
                deltaMs = longPressSeekState.accumulatedMs,
                targetPositionMs = longPressSeekState.targetPositionMs,
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 72.dp,
                    bottom = 96.dp,
                ),
            )
        }
    }
}
