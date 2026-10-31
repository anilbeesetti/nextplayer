package dev.anilbeesetti.nextplayer.feature.player.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getSystemService
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import dev.anilbeesetti.nextplayer.core.model.Font
import dev.anilbeesetti.nextplayer.feature.player.extensions.toTypeface
import dev.anilbeesetti.nextplayer.feature.player.state.CuesState
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitlePositionState

@OptIn(UnstableApi::class)
@Composable
fun SubtitleView(
    modifier: Modifier = Modifier,
    cuesState: CuesState,
    positionState: SubtitlePositionState,
    isInPictureInPictureMode: Boolean,
    configuration: SubtitleConfiguration,
) {
    // Playback keeps going while the subtitle is moved; the text on screen stays put until release.
    var frozenCues by remember { mutableStateOf<List<Cue>?>(null) }
    LaunchedEffect(positionState.isDragging) {
        frozenCues = cuesState.currentCues?.cues.takeIf { positionState.isDragging }
    }
    val displayedCues = frozenCues.takeIf { positionState.isDragging }
        ?: cuesState.currentCues?.cues

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (isInPictureInPictureMode) return@graphicsLayer
                translationX = positionState.offset.x * size.width
                translationY = positionState.offset.y * size.height
            },
        factory = { context ->
            SubtitleView(context).apply {
                setStyle(
                    createCaptionStyle(
                        context,
                        configuration,
                        highlighted = false,
                    ),
                )
                if (!configuration.useSystemCaptionStyle) {
                    setFixedTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        configuration.textSize.toFloat(),
                    )
                }
                setApplyEmbeddedStyles(configuration.applyEmbeddedStyles)
            }
        },
        update = { subtitleView ->
            subtitleView.setStyle(
                createCaptionStyle(
                    context = subtitleView.context,
                    configuration = configuration,
                    highlighted = positionState.isDragging,
                ),
            )
            subtitleView.setCues(displayedCues)
            if (isInPictureInPictureMode) {
                subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
            } else {
                subtitleView.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    configuration.textSize.toFloat(),
                )
            }
        },
    )
}

// Translucent grey box behind the text while the subtitle is being moved.
private val DRAG_HIGHLIGHT_COLOR = 0x33C8C8C8.toInt()

@OptIn(UnstableApi::class)
private fun createCaptionStyle(
    context: Context,
    configuration: SubtitleConfiguration,
    highlighted: Boolean,
): CaptionStyleCompat {
    val captioningManager = getSystemService(
        context,
        CaptioningManager::class.java,
    )
    val style = if (configuration.useSystemCaptionStyle && captioningManager != null) {
        CaptionStyleCompat.createFromCaptionStyle(captioningManager.userStyle)
    } else {
        CaptionStyleCompat(
            android.graphics.Color.WHITE,
            android.graphics.Color.BLACK.takeIf { configuration.showBackground }
                ?: android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            android.graphics.Color.BLACK,
            Typeface.create(
                configuration.font.toTypeface(),
                Typeface.BOLD.takeIf { configuration.textBold } ?: Typeface.NORMAL,
            ),
        )
    }
    if (!highlighted) return style
    return CaptionStyleCompat(
        style.foregroundColor,
        DRAG_HIGHLIGHT_COLOR,
        style.windowColor,
        style.edgeType,
        style.edgeColor,
        style.typeface,
    )
}

@Stable
data class SubtitleConfiguration(
    val useSystemCaptionStyle: Boolean,
    val showBackground: Boolean,
    val font: Font,
    val textSize: Int,
    val textBold: Boolean,
    val applyEmbeddedStyles: Boolean,
)
