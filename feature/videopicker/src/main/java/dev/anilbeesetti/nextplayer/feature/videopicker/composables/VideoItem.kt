package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun VideoItem(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    .widthIn(max = 450.dp)
                    .fillMaxWidth(0.45f)
                    .aspectRatio(16f / 10f)
            ) {
                if (video.uriString.isNotEmpty()) {
                    GlideImage(
                        imageModel = { video.uriString },
                        imageOptions = ImageOptions(
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center
                        )
                    )
                }
                InfoChip(
                    text = video.formattedDuration,
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.BottomEnd),
                    backgroundColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.small.copy(CornerSize(3.dp))
                )
            }
        },
        headlineContent = {
            Text(
                text = video.displayName,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = video.path,
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoChip(
                    text = video.formattedFileSize,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
                if (video.width > 0 && video.height > 0) {
                    InfoChip(
                        text = "${video.width} x ${video.height}",
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        },
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        )
    )
}

@DayNightPreview
@DevicePreviews
@Composable
fun VideoItemPreview() {
    NextPlayerTheme {
        Surface {
            VideoItem(
                video = Video.sample,
                onClick = {}
            )
        }
    }
}
