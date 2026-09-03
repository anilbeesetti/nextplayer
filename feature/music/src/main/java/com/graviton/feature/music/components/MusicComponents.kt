package com.graviton.feature.music.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.graviton.core.model.AudioTrack
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.music.artwork.MediaArtwork

/** Actions offered by the song overflow menu. Every one of these is wired to real behaviour. */
@androidx.compose.runtime.Immutable
data class TrackActions(
    val onPlay: () -> Unit,
    val onPlayNext: () -> Unit,
    val onEnqueue: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onShare: () -> Unit,
    val onInformation: () -> Unit,
)

/**
 * A song row.
 *
 * The active row is marked with both a container colour *and* a "Now playing" supporting line, so
 * the state is never communicated by colour alone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: AudioTrack,
    active: Boolean,
    playing: Boolean,
    isFavorite: Boolean,
    actions: TrackActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = actions.onPlay,
                onLongClick = { menuOpen = true },
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        ),
        leadingContent = {
            MediaArtwork(
                artworkUri = track.artworkUriString,
                mediaUri = track.uriString,
                modifier = Modifier.size(52.dp),
            )
        },
        headlineContent = {
            Text(
                text = track.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Text(
                text = if (active) {
                    stringResource(R.string.now_playing)
                } else {
                    "${track.displayArtist} • ${track.displayAlbum}"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTrackDuration(track.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = NextIcons.MoreVert,
                            contentDescription = stringResource(R.string.song_options),
                        )
                    }
                    TrackOverflowMenu(
                        expanded = menuOpen,
                        active = active,
                        playing = playing,
                        isFavorite = isFavorite,
                        actions = actions,
                        onDismiss = { menuOpen = false },
                    )
                }
            }
        },
    )
}

@Composable
private fun TrackOverflowMenu(
    expanded: Boolean,
    active: Boolean,
    playing: Boolean,
    isFavorite: Boolean,
    actions: TrackActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(
                        when {
                            active && playing -> R.string.pause
                            active -> R.string.resume_playback
                            else -> R.string.play
                        },
                    ),
                )
            },
            onClick = { onDismiss(); actions.onPlay() },
            leadingIcon = {
                Icon(
                    imageVector = if (active && playing) NextIcons.Pause else NextIcons.Play,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.play_next)) },
            onClick = { onDismiss(); actions.onPlayNext() },
            leadingIcon = { Icon(NextIcons.PlayNext, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_to_queue)) },
            onClick = { onDismiss(); actions.onEnqueue() },
            leadingIcon = { Icon(NextIcons.QueueMusic, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_to_playlist)) },
            onClick = { onDismiss(); actions.onAddToPlaylist() },
            leadingIcon = { Icon(NextIcons.PlaylistAdd, contentDescription = null) },
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites,
                    ),
                )
            },
            onClick = { onDismiss(); actions.onToggleFavorite() },
            leadingIcon = {
                Icon(
                    imageVector = if (isFavorite) NextIcons.Favorite else NextIcons.FavoriteOutline,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share)) },
            onClick = { onDismiss(); actions.onShare() },
            leadingIcon = { Icon(NextIcons.Share, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.video_information)) },
            onClick = { onDismiss(); actions.onInformation() },
            leadingIcon = { Icon(NextIcons.Info, contentDescription = null) },
        )
    }
}

/** Section heading with an optional "Show all" affordance. */
@Composable
fun MusicSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (onShowAll != null) {
            TextButton(onClick = onShowAll) {
                Text(stringResource(R.string.show_all))
            }
        }
    }
}

/**
 * A compact square card used by the horizontal Home carousels.
 *
 * Height is fixed so the row does not reflow while artwork loads.
 */
@Composable
fun MusicTile(
    title: String,
    subtitle: String,
    artworkUri: String?,
    mediaUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallback: ImageVector = NextIcons.Audio,
) {
    Column(
        modifier = modifier
            .width(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MediaArtwork(
            artworkUri = artworkUri,
            mediaUri = mediaUri,
            modifier = Modifier.size(120.dp),
            fallback = fallback,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A pill-shaped quick-access entry for the Home dashboard. */
@Composable
fun QuickAccessChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The label already names the action, so the icon stays decorative to avoid a
            // duplicate TalkBack announcement.
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Centered empty/permission/error state shared by every music section. */
@Composable
fun MusicEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(18.dp).size(30.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

/**
 * A one-shot fade/rise used for list items.
 *
 * The animation is finite and driven by a single [animateFloatAsState], so it settles and stops
 * costing frames; when [enabled] is false the content renders with no animation at all.
 */
@Composable
fun Modifier.musicItemAppearance(enabled: Boolean, index: Int = 0): Modifier {
    if (!enabled) return this
    var visible by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200, delayMillis = (index * 30).coerceAtMost(240)),
        label = "musicItemAppearance",
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 16.dp.toPx()
    }
}

/** A thin divider-like spacer that keeps carousels visually separated without a hard rule. */
@Composable
fun MusicSectionSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clearAndSetSemantics { }
            .background(Color.Transparent),
    )
}

/** Formats a track duration as m:ss (or h:mm:ss when it runs over an hour). */
fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
