package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.NextButtonState
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.PreviousButtonState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.buttons.NextButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayPauseButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PreviousButton

@OptIn(UnstableApi::class)
@Composable
fun ControlsMiddleView(
    modifier: Modifier = Modifier,
    playPauseButtonState: PlayPauseButtonState,
    previousButtonState: PreviousButtonState,
    nextButtonState: NextButtonState,
) {
    val playPauseFocusRequester = remember { FocusRequester() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties {
                onEnter = { playPauseFocusRequester.requestFocus() }
            }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviousButton(state = previousButtonState)
        PlayPauseButton(
            state = playPauseButtonState,
            modifier = Modifier.focusRequester(playPauseFocusRequester),
        )
        NextButton(state = nextButtonState)
    }
}

@OptIn(UnstableApi::class)
@Preview
@Composable
private fun ControlsMiddleViewPreview() {
    NextPlayerTheme(darkTheme = true) {
        Surface {
            ControlsMiddleView(
                playPauseButtonState = rememberPlayPauseButtonState(null),
                previousButtonState = rememberPreviousButtonState(null),
                nextButtonState = rememberNextButtonState(null),
            )
        }
    }
}
