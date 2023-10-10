package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Size
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoItem(
    video: Video,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier
) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    .width(thumbWidthDp(size = preferences.thumbnailSize))
                    .aspectRatio(16f / 10f)
            ) {
                Icon(
                    imageVector = NextIcons.Video,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surfaceColorAtElevation(100.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.5f)
                )
                if (video.uriString.isNotEmpty() && preferences.showThumbnailField) {
                    GlideImage(
                        imageModel = { video.uriString },
                        imageOptions = ImageOptions(
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center
                        ),
                        modifier = Modifier.fillMaxSize()
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
                        shape = MaterialTheme.shapes.extraSmall
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = if (preferences.showExtensionField) video.nameWithExtension else video.displayName,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            if (preferences.showPathField) {
                Text(
                    text = video.path,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (preferences.showSizeField) {
                    InfoChip(text = video.formattedFileSize)
                }
                if (preferences.showResolutionField && video.height > 0) {
                    InfoChip(text = "${video.height}p")
                }
            }
        },
        modifier = modifier
    )
}

@DayNightPreview
@DevicePreviews
@Composable
fun VideoItemPreview() {
    NextPlayerTheme {
        Surface {
            VideoItem(video = Video.sample, preferences = ApplicationPreferences())
        }
    }
}


@Composable
private fun thumbWidthDp(size: Size): Dp {
    val localConfig = LocalConfiguration.current
    val maxScreenWidthDp = localConfig.screenWidthDp.dp * 0.4f
    return when (size) {
        Size.COMPACT -> min(130.dp, maxScreenWidthDp)
        Size.MEDIUM -> min(165.dp, maxScreenWidthDp)
        Size.LARGE -> min(200.dp, maxScreenWidthDp)
    }
}