package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.labelRes

@OptIn(UnstableApi::class)
@Composable
fun ControlsTopView(
    modifier: Modifier = Modifier,
    title: String,
    videoDecoderMode: DecoderMode?,
    inlineActionCount: Int,
    onBackClick: () -> Unit = {},
    onDecoderClick: () -> Unit = {},
    onAudioClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
) {
    val firstControlFocusRequester = remember { FocusRequester() }
    val systemBarsPadding = topControlsInsets.asPaddingValues()
    val decoderDescription = stringResource(R.string.select_decoders)
    // Add top spacing only when the system bars don't already provide it (e.g. on TV / landscape).
    val extraTopPadding = if (systemBarsPadding.calculateTopPadding() == 0.dp) 16.dp else 0.dp
    Row(
        modifier = modifier
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(horizontal = TopControlsHorizontalPadding)
            .padding(bottom = 16.dp)
            .padding(top = extraTopPadding)
            .fillMaxWidth()
            .focusProperties { onEnter = { firstControlFocusRequester.requestFocus() } }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlayerButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = null,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(lineBreak = LineBreak.Heading),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerButton(
                modifier = Modifier
                    .semantics { contentDescription = decoderDescription }
                    .focusRequester(focusRequester = firstControlFocusRequester),
                onClick = onDecoderClick,
            ) {
                Text(
                    text = stringResource((videoDecoderMode ?: DecoderMode.HARDWARE).labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            if (inlineActionCount >= 1) {
                PlayerButton(onClick = onPlaylistClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playlist),
                        contentDescription = stringResource(R.string.now_playing),
                    )
                }
            }
            if (inlineActionCount >= 2) {
                PlayerButton(onClick = onAudioClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_audio_track),
                        contentDescription = stringResource(R.string.audio_track),
                    )
                }
            }
            if (inlineActionCount >= 3) {
                PlayerButton(onClick = onSubtitleClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_subtitle_track),
                        contentDescription = stringResource(R.string.subtitle_track),
                    )
                }
            }
            PlayerButton(onClick = onMoreClick) {
                Icon(
                    imageVector = NextIcons.MoreVert,
                    contentDescription = stringResource(R.string.more),
                )
            }
        }
    }
}

private val topControlsInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

@Composable
internal fun inlineTopActionCount(width: Dp): Int {
    val insets = topControlsInsets.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val available = width - insets.calculateLeftPadding(direction) -
        insets.calculateRightPadding(direction) - TopControlsHorizontalPadding * 2
    // Reserve one action slot for More after leaving room for the title.
    val actionSlots = ((available - FixedContentWidth - MinTitleWidth) / ActionSlotWidth).toInt()
    return (actionSlots - 1).coerceAtLeast(0)
}

private val TopControlsHorizontalPadding = 16.dp
private val MinTitleWidth = 160.dp

// 40dp button + 8dp spacing.
private val ActionSlotWidth = 48.dp

// Back button, its gap, the title-to-actions gap and the decoder button.
private val FixedContentWidth = 40.dp + 16.dp + 16.dp + 48.dp

@Preview
@Composable
private fun ControlsTopViewPreview() {
    NextPlayerTheme(darkTheme = true) {
        Surface {
            BoxWithConstraints {
                ControlsTopView(
                    title = "Title",
                    videoDecoderMode = DecoderMode.HARDWARE,
                    inlineActionCount = inlineTopActionCount(maxWidth),
                )
            }
        }
    }
}
