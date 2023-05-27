package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun VideoItem(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val formattedSize = remember { Formatter.formatFileSize(context, video.size) }
    val formattedDuration = remember { Utils.formatDurationMillis(video.duration) }

    var thumbHeight by remember { mutableStateOf(0) }
    var contentHeight by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            )
    ) {
        Row(
            verticalAlignment = if (contentHeight > thumbHeight) Alignment.CenterVertically else Alignment.Top,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .onSizeChanged { thumbHeight = it.height }
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(0.45f)
                        .aspectRatio(16f / 10f),
                    content = {
                        if (video.uriString.isNotEmpty()) {
                            GlideImage(
                                imageModel = { video.uri },
                                imageOptions = ImageOptions(
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.Center
                                )
                            )
                        }
                    }
                )
                InfoChip(
                    text = formattedDuration,
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.BottomEnd),
                    backgroundColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.small.copy(CornerSize(3.dp))
                )
            }
            Column(
                modifier = Modifier
                    .onSizeChanged { contentHeight = it.height }
                    .padding(start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = video.nameWithExtension,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.path,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (video.subtitleTracks.isNotEmpty()) {
                        InfoChip(text = "SUB")
                    }
                    InfoChip(text = formattedSize, modifier = Modifier.padding(vertical = 5.dp))
                    InfoChip(text = "${video.width} x ${video.height}")
                }
            }
        }
    }
}

@DayNightPreview
@Composable
fun VideoItemPreview() {
    NextPlayerTheme {
        Surface {
            VideoItem(
                video = Video(
                    id = 8,
                    path = "/storage/emulated/0/Download/Avengers Endgame (2019) BluRay x264.mp4",
                    uriString = "",
                    nameWithExtension = "Avengers Endgame (2019) BluRay x264.mp4",
                    duration = 1000,
                    displayName = "Avengers Endgame (2019) BluRay x264",
                    width = 1920,
                    height = 1080,
                    size = 1000
                ),
                onClick = {}
            )
        }
    }
}
