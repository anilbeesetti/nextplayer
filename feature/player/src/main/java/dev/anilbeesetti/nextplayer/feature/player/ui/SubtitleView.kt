package dev.anilbeesetti.nextplayer.feature.player.ui

import android.graphics.Typeface
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import dev.anilbeesetti.nextplayer.core.model.Font
import dev.anilbeesetti.nextplayer.feature.player.extensions.toTypeface
import dev.anilbeesetti.nextplayer.feature.player.state.rememberCuesState
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerSubtitleView(
    modifier: Modifier = Modifier,
    player: Player,
    isInPictureInPictureMode: Boolean,
    configuration: SubtitleConfiguration,
    subtitleDelayMs: Long = 0L,
    onDelayChanged: (Long) -> Unit = {},
) {
    val cuesState = rememberCuesState(player)

    // Force subtitle renderer to resync when delay changes
    LaunchedEffect(subtitleDelayMs) {
        if (player.isPlaying) {
            val pos = player.currentPosition
            player.seekTo(pos + 1)
            delay(16)
            player.seekTo(pos)
            onDelayChanged(subtitleDelayMs)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SubtitleView(context).apply {
                val captioningManager =
                    context.getSystemService(CaptioningManager::class.java)

                if (configuration.useSystemCaptionStyle && captioningManager != null) {
                    setStyle(
                        CaptionStyleCompat.createFromCaptionStyle(
                            captioningManager.userStyle
                        )
                    )
                } else {
                    setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            if (configuration.showBackground)
                                android.graphics.Color.BLACK
                            else
                                android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            android.graphics.Color.BLACK,
                            Typeface.create(
                                configuration.font.toTypeface(),
                                if (configuration.textBold) Typeface.BOLD else Typeface.NORMAL
                            )
                        )
                    )
                    setFixedTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        configuration.textSize.toFloat()
                    )
                }

                setApplyEmbeddedStyles(configuration.applyEmbeddedStyles)
            }
        },
        update = { subtitleView ->
            // ✅ APPLY ACTUAL DELAY TO CUES
            val delayedCues = cuesState.cues?.map { cue ->
                cue.buildUpon()
                    .setStartTimeMs(cue.startTimeMs + subtitleDelayMs)
                    .setEndTimeMs(cue.endTimeMs + subtitleDelayMs)
                    .build()
            }

            subtitleView.setCues(delayedCues)

            if (isInPictureInPictureMode) {
                subtitleView.setFractionalTextSize(
                    SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                )
            } else {
                subtitleView.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    configuration.textSize.toFloat()
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
