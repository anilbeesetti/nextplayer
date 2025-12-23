package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.extensions.nameRes

@Composable
fun BoxScope.VideoContentScaleSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    videoContentScale: VideoContentScale,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.video_zoom),
    ) {
        Column(modifier = modifier.selectableGroup()) {
            VideoContentScale.entries.forEach { contentScale ->
                RadioButtonRow(
                    selected = contentScale == videoContentScale,
                    text = stringResource(contentScale.nameRes()),
                    onClick = {
                        onVideoContentScaleChanged(contentScale)
                        onDismiss()
                    },
                )
            }
        }
    }
}
