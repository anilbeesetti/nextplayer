package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoItem(
    video: Video,
    preferences: ApplicationPreferences,
    multiSelectEnabled: Boolean,
    isSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    .width(min(150.dp, LocalConfiguration.current.screenWidthDp.dp * 0.35f))
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
                if (preferences.showThumbnailField) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(video.thumbnailPath)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        alignment = Alignment.Center,
                        contentScale = ContentScale.Crop,
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
                if (multiSelectEnabled) {
                    Checkbox(
                        checked = video.isSelected,
                        onCheckedChange = {
                                          isSelected(it)
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .align(Alignment.BottomStart)
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
                    text = video.path.substringBeforeLast("/"),
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
// @DevicePreviews
@Composable
fun VideoItemPreview() {
    NextPlayerTheme {
        Surface {
            VideoItem(video = Video.sample, multiSelectEnabled = true, isSelected = {}, preferences = ApplicationPreferences())
        }
    }
}
