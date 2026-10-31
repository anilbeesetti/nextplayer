package dev.anilbeesetti.nextplayer.feature.player.ui

import android.graphics.Typeface
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getSystemService
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
                val captioningManager =
                    getSystemService(
                        context,
                        CaptioningManager::class.java,
                    ) ?: return@apply
                if (configuration.useSystemCaptionStyle) {
                    val systemCaptionStyle = CaptionStyleCompat.createFromCaptionStyle(
                        captioningManager.userStyle,
                    )
                    setStyle(systemCaptionStyle)
                } else {
                    val userStyle = CaptionStyleCompat(
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
                    setStyle(userStyle)
                    setFixedTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        configuration.textSize.toFloat(),
                    )
                }
                setApplyEmbeddedStyles(configuration.applyEmbeddedStyles)
            }
        },
        update = { subtitleView ->
            subtitleView.setCues(cuesState.currentCues?.cues)
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

@Stable
data class SubtitleConfiguration(
    val useSystemCaptionStyle: Boolean,
    val showBackground: Boolean,
    val font: Font,
    val textSize: Int,
    val textBold: Boolean,
    val applyEmbeddedStyles: Boolean,
)
