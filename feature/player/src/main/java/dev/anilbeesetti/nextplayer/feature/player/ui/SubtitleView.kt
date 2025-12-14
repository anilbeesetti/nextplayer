package dev.anilbeesetti.nextplayer.feature.player.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getSystemService
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.text.Cue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.feature.player.extensions.toTypeface

@Composable
fun SubtitleView(
    modifier: Modifier = Modifier,
    player: Player,
    playerPreferences: PlayerPreferences,
) {
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    LaunchedEffect(Unit) {
        player.listen { events ->
            if (events.contains(Player.EVENT_CUES)) {
                currentCues = player.currentCues.cues
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SubtitleView(context).apply {
                val captioningManager = getSystemService(context, CaptioningManager::class.java) ?: return@apply
                if (playerPreferences.useSystemCaptionStyle) {
                    val systemCaptionStyle = CaptionStyleCompat.createFromCaptionStyle(captioningManager.userStyle)
                    setStyle(systemCaptionStyle)
                } else {
                    val userStyle = CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.BLACK.takeIf { playerPreferences.subtitleBackground } ?: android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        android.graphics.Color.BLACK,
                        Typeface.create(
                            playerPreferences.subtitleFont.toTypeface(),
                            Typeface.BOLD.takeIf { playerPreferences.subtitleTextBold } ?: Typeface.NORMAL,
                        ),
                    )
                    setStyle(userStyle)
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, playerPreferences.subtitleTextSize.toFloat())
                }
                setApplyEmbeddedStyles(playerPreferences.applyEmbeddedStyles)
            }
        },
        update = { subtitleView ->
            subtitleView.setCues(currentCues)
        },
    )
}