package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

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
    onShare: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onInfo: (() -> Unit)?,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.more),
    ) {
        FlowRow(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in MoreOption.entries) {
                MoreOptionButton(
                    label = stringResource(option.labelRes),
                    painter = painterResource(option.iconRes),
                    onClick = { onOptionClick(option.overlay) },
                )
            }
            MoreOptionButton(
                label = stringResource(R.string.share),
                painter = rememberVectorPainter(NextIcons.Share),
                onClick = onShare,
            )
            MoreOptionButton(
                label = stringResource(R.string.rename),
                painter = rememberVectorPainter(NextIcons.Edit),
                onClick = onRename,
            )
            MoreOptionButton(
                label = stringResource(R.string.info),
                painter = rememberVectorPainter(NextIcons.Info),
                onClick = onInfo,
            )
        }
    }
}

@Composable
private fun MoreOptionButton(label: String, painter: Painter, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier.widthIn(max = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalIconButton(
            enabled = onClick != null,
            onClick = { onClick?.invoke() },
        ) {
            Icon(
                painter = painter,
                contentDescription = label,
            )
        }
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
