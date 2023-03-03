package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import dev.anilbeesetti.nextplayer.core.ui.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoItemView(
    videoItem: VideoItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            )
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.45f)
                    .aspectRatio(16f / 10f),
                content = {
                    if (videoItem.contentUri != Uri.EMPTY) {
                        GlideImage(
                            imageModel = { videoItem.contentUri },
                            imageOptions = ImageOptions(
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center
                            )
                        )
                    }
                }
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = videoItem.nameWithExtension,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoChip(text = formatMillis(videoItem.duration))
                }
            }
        }
    }
}

fun formatMillis(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes) - TimeUnit.HOURS.toSeconds(
        hours
    )
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@DayNightPreview
@Composable
fun VideoItemPreview() {
    NextPlayerTheme {
        Surface {
            VideoItemView(
                videoItem = VideoItem(
                    id = 8,
                    path = "/storage/emulated/0/Download/Avengers Endgame (2019) BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "Avengers Endgame (2019) BluRay x264.mp4",
                    duration = 1000,
                    displayName = "Avengers Endgame (2019) BluRay x264",
                    width = 1920,
                    height = 1080
                ),
                onClick = {}
            )
        }
    }
}
