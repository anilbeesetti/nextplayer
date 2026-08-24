package com.graviton.feature.music

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.music.artwork.MediaArtwork
import com.graviton.feature.music.player.MusicPlayerActivity
import com.graviton.feature.player.PlayerActivity
import com.graviton.feature.player.utils.PlayerApi
import kotlinx.coroutines.delay

@Composable
fun NowPlayingMiniBar(
    modifier: Modifier = Modifier,
) {
    val connection = rememberMusicSession()
    val controller = connection.controller
    val snapshot = rememberMusicPlaybackSnapshot(controller)
    LaunchedEffect(snapshot) {
        while (snapshot != null) {
            snapshot.refresh()
            delay(500)
        }
    }
    if (snapshot?.mediaId == null) return

    val context = LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable {
                if (snapshot.isMusic) {
                    context.startActivity(Intent(context, MusicPlayerActivity::class.java))
                } else {
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = snapshot.mediaId?.let(Uri::parse)
                        putExtra(PlayerApi.API_KEEP_SESSION, true)
                    }
                    context.startActivity(intent)
                }
            },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaArtwork(
                    artworkUri = snapshot.artworkUri?.toString(),
                    mediaUri = snapshot.mediaId,
                    artworkData = snapshot.artworkData,
                    modifier = Modifier.size(48.dp),
                    fallback = if (snapshot.isMusic) NextIcons.Audio else NextIcons.Movie,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = snapshot.title.ifBlank { "Now playing" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = snapshot.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { if (controller?.isPlaying == true) controller.pause() else controller?.play() }) {
                    Icon(
                        imageVector = if (snapshot.isPlaying) NextIcons.Pause else NextIcons.Play,
                        contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
                    )
                }
            }
            if (snapshot.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }
}
