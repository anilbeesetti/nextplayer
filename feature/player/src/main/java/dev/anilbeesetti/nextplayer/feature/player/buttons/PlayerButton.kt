package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.milliseconds

val PlayerButtonBlackAlpha = Color.Black.copy(alpha = 0.3f)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val viewConfiguration = LocalViewConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    LaunchedEffect(interactionSource) {
        var isLongPressClicked = false
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isLongPressClicked = false
                    delay(viewConfiguration.longPressTimeoutMillis.milliseconds)
                    onLongClick?.let {
                        isLongPressClicked = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        it.invoke()
                    }
                }

                is PressInteraction.Release -> {
                    if (!isLongPressClicked) {
                        onClick()
                    }
                }
            }
        }
    }

    val colors = if (LocalUseMaterialYouControls.current) {
        IconButtonDefaults.filledTonalIconButtonColors()
    } else {
        IconButtonDefaults.iconButtonColors(
            contentColor = Color.White,
            containerColor = containerColor,
        )
    }

    val rippleConfiguration = if (LocalUseMaterialYouControls.current) {
        LocalRippleConfiguration.current
    } else {
        RippleConfiguration(
            color = Color.White,
            rippleAlpha = RippleAlpha(
                pressedAlpha = 0.5f,
                focusedAlpha = 0.5f,
                draggedAlpha = 0.5f,
                hoveredAlpha = 0.5f
            )
        )
    }

    CompositionLocalProvider(
        LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor,
        LocalRippleConfiguration provides rippleConfiguration
    ) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(if (enabled) colors.containerColor else colors.disabledContainerColor)
                .clickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    onClick = {},
                )
                .padding(contentPadding)
                .tvFocusRing(isTv),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
