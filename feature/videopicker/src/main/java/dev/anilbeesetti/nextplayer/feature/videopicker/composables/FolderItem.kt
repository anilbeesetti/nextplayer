package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FolderItem(
    folder: Folder,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        )
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = NextIcons.Folder,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .sizeIn(maxWidth = 250.dp)
                    .fillMaxWidth(0.45f)
                    .aspectRatio(1f)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = folder.name,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = folder.path,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoChip(
                        text = "${folder.mediaCount} ${
                        stringResource(
                            id = R.string.video.takeIf { folder.mediaCount == 1 } ?: R.string.videos
                        )
                        }",
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@DayNightPreview
@Composable
fun FolderItemPreview() {
    NextPlayerTheme {
        FolderItem(
            folder = Folder(
                name = "Folder 1",
                path = "/storage/emulated/0/DCIM/Camera/Live Photos",
                mediaSize = 1000,
                mediaCount = 1,
                isExcluded = false
            ),
            onClick = { }
        )
    }
}
