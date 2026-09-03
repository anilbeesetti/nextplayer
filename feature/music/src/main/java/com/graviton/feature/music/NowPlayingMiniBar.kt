package com.graviton.feature.music

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.music.artwork.MediaArtwork
import com.graviton.feature.music.player.MusicPlayerActivity
import com.graviton.feature.player.PlayerActivity
import com.graviton.feature.player.utils.PlayerApi

/**
 * The compact now-playing bar.
 *
 * It is designed to sit directly on top of the [androidx.compose.material3.NavigationBar]: the
 * bottom corners are square and it carries no outer margin, so bar and nav read as a single
 * surface stack rather than a floating card.
 */
@Composable
fun NowPlayingMiniBar(
    modifier: Modifier = Modifier,
) {
    val connection = rememberMusicSession()
    val controller = connection.controller
    val snapshot = rememberMusicPlaybackSnapshot(controller)
    val context = LocalContext.current

    AnimatedVisibility(
        visible = snapshot?.mediaId != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        // The snapshot is captured so the exit animation still has content to draw.
        val current = snapshot ?: return@AnimatedVisibility
        val title = current.title.ifBlank { stringResource(R.string.now_playing) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.mini_player_expand)) {
                    if (current.isMusic) {
                        context.startActivity(Intent(context, MusicPlayerActivity::class.java))
                    } else {
                        context.startActivity(
                            Intent(context, PlayerActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                data = current.mediaId?.toUri()
                                putExtra(PlayerApi.API_KEEP_SESSION, true)
                            },
                        )
                    }
                },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaArtwork(
                        artworkUri = current.artworkUri?.toString(),
                        mediaUri = current.mediaId,
                        artworkData = current.artworkData,
                        modifier = Modifier.size(44.dp),
                        fallback = if (current.isMusic) NextIcons.Audio else NextIcons.Movie,
                        corner = 10.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    // The row itself is the "open now playing" target, so the text is folded into
                    // that one announcement instead of being read as separate nodes.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics { },
                    ) {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        if (current.artist.isNotBlank()) {
                            Text(
                                text = current.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (current.isMusic) {
                        IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                            Icon(
                                imageVector = NextIcons.SkipPrevious,
                                contentDescription = stringResource(R.string.previous),
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            if (controller?.isPlaying == true) controller.pause() else controller?.play()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = if (current.isPlaying) NextIcons.Pause else NextIcons.Play,
                            contentDescription = stringResource(
                                if (current.isPlaying) R.string.pause else R.string.play,
                            ),
                        )
                    }
                    if (current.isMusic) {
                        IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                            Icon(
                                imageVector = NextIcons.SkipNext,
                                contentDescription = stringResource(R.string.next),
                            )
                        }
                    }
                }
                if (current.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clearAndSetSemantics { },
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
