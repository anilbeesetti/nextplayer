package com.graviton.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R

@Composable
fun BoxScope.ToolsPanelView(
    modifier: Modifier = Modifier,
    show: Boolean,
    onDismiss: () -> Unit,
    onVideoContentScaleClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = "Tools",
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            ListItem(
                headlineContent = { Text("Aspect Ratio") },
                leadingContent = { Icon(painterResource(R.drawable.ic_aspect_ratio), contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    onVideoContentScaleClick()
                }
            )
            ListItem(
                headlineContent = { Text("Playback Speed") },
                leadingContent = { Icon(painterResource(R.drawable.ic_speed), contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    onPlaybackSpeedClick()
                }
            )
            // Added placeholders for the requested tools panel.
            ListItem(
                headlineContent = { Text("Display Settings") },
                modifier = Modifier.fillMaxWidth().clickable { onDismiss() }
            )
            ListItem(
                headlineContent = { Text("MediaInfo Dialog") },
                modifier = Modifier.fillMaxWidth().clickable { onDismiss() }
            )
            ListItem(
                headlineContent = { Text("Share") },
                modifier = Modifier.fillMaxWidth().clickable { onDismiss() }
            )
            ListItem(
                headlineContent = { Text("Cut/Trim") },
                modifier = Modifier.fillMaxWidth().clickable { onDismiss() }
            )
            ListItem(
                headlineContent = { Text("Bookmarks") },
                modifier = Modifier.fillMaxWidth().clickable { onDismiss() }
            )
        }
    }
}
