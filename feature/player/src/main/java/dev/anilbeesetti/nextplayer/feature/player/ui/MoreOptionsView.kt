package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R

private enum class MoreOption(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val overlay: OverlayView,
) {
    PLAYLIST(
        R.string.now_playing,
        R.drawable.ic_playlist,
        OverlayView.PLAYLIST,
    ),
    AUDIO(
        R.string.audio_track,
        R.drawable.ic_audio_track,
        OverlayView.AUDIO_SELECTOR,
    ),
    SUBTITLE(
        R.string.subtitle_track,
        R.drawable.ic_subtitle_track,
        OverlayView.SUBTITLE_SELECTOR,
    ),
}

@Composable
fun BoxScope.MoreOptionsView(
    modifier: Modifier = Modifier,
    show: Boolean,
    onOptionClick: (OverlayView) -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.more),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(MoreOption.entries) { option ->
                val label = stringResource(option.labelRes)
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = { onOptionClick(option.overlay) }) {
                        Icon(
                            painter = painterResource(option.iconRes),
                            contentDescription = label,
                        )
                    }
                    Text(
                        text = label,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
