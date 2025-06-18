package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.components.ListItemComponent
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@Composable
fun VideoItem(
    video: Video,
    isRecentlyPlayedVideo: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
) {
    when (preferences.mediaLayoutMode) {
        MediaLayoutMode.LIST -> VideoListItem(
            video = video,
            isRecentlyPlayedVideo = isRecentlyPlayedVideo,
            preferences = preferences,
            modifier = modifier,
        )
        MediaLayoutMode.GRID -> VideoGridItem(
            video = video,
            isRecentlyPlayedVideo = isRecentlyPlayedVideo,
            preferences = preferences,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoListItem(
    video: Video,
    isRecentlyPlayedVideo: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
) {
    ListItemComponent(
        colors = ListItemDefaults.colors(
            headlineColor = if (isRecentlyPlayedVideo && preferences.markLastPlayedMedia) {
                MaterialTheme.colorScheme.primary
            } else {
                ListItemDefaults.colors().headlineColor
            },
            supportingColor = if (isRecentlyPlayedVideo && preferences.markLastPlayedMedia) {
                MaterialTheme.colorScheme.primary
            } else {
                ListItemDefaults.colors().supportingTextColor
            },
        ),
        leadingContent = {
            ThumbnailView(
                video = video,
                preferences = preferences,
                modifier = Modifier
                    .width(min(150.dp, LocalConfiguration.current.screenWidthDp.dp * 0.35f)),
            )
        },
        headlineContent = {
            Text(
                text = if (preferences.showExtensionField) video.nameWithExtension else video.displayName,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            if (preferences.showPathField) {
                Text(
                    text = video.path.substringBeforeLast("/"),
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (preferences.showSizeField) {
                    InfoChip(text = video.formattedFileSize)
                }
                if (preferences.showResolutionField && video.height > 0) {
                    InfoChip(text = "${video.height}p")
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun VideoGridItem(
    video: Video,
    isRecentlyPlayedVideo: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThumbnailView(
            video = video,
            preferences = preferences,
        )
        Text(
            text = if (preferences.showExtensionField) video.nameWithExtension else video.displayName,
            maxLines = 2,
            style = MaterialTheme.typography.titleMedium,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (isRecentlyPlayedVideo && preferences.markLastPlayedMedia) {
                MaterialTheme.colorScheme.primary
            } else {
                ListItemDefaults.colors().headlineColor
            },
        )
    }
}

@Composable
private fun ThumbnailView(
    modifier: Modifier = Modifier,
    video: Video,
    preferences: ApplicationPreferences,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .aspectRatio(16f / 10f),
    ) {
        Icon(
            imageVector = NextIcons.Video,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceColorAtElevation(100.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.5f),
        )
        if (preferences.showThumbnailField && !video.thumbnailPath.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.thumbnailPath)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (preferences.showDurationField) {
            InfoChip(
                text = video.formattedDuration,
                modifier = Modifier
                    .padding(5.dp)
                    .align(Alignment.BottomEnd),
                backgroundColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraSmall,
            )
        }

        if (preferences.showPlayedProgress && video.playedPercentage > 0) {
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(video.playedPercentage)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
fun VideoItemRecentlyPlayedPreview() {
    NextPlayerTheme {
        Surface {
            VideoListItem(
                video = Video.sample,
                preferences = ApplicationPreferences(),
                isRecentlyPlayedVideo = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun VideoItemPreview() {
    NextPlayerTheme {
        Surface {
            VideoListItem(
                video = Video.sample,
                preferences = ApplicationPreferences(),
                isRecentlyPlayedVideo = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun VideoGridItemPreview() {
    NextPlayerTheme {
        VideoGridItem(
            video = Video.sample,
            preferences = ApplicationPreferences(),
            isRecentlyPlayedVideo = true,
        )
    }
}
