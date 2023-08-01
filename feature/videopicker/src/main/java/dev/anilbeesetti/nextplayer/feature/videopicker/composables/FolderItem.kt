package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.anilbeesetti.nextplayer.core.model.Directory
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FolderItem(
    directory: Directory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {

    ListItem(
        leadingContent = {
            Icon(
                imageVector = NextIcons.Folder,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .negativeVerticalPadding(8.dp)
                    .sizeIn(maxWidth = 250.dp)
                    .fillMaxWidth(0.45f)
                    .aspectRatio(1f)
            )
        },
        headlineContent = {
            Text(
                text = directory.name,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = directory.path,
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
                    text = "${directory.mediaCount} ${
                    stringResource(
                        id = R.string.video.takeIf { directory.mediaCount == 1 } ?: R.string.videos
                    )
                    }",
                    modifier = Modifier.padding(vertical = 5.dp)
                )
                InfoChip(
                    text = directory.formattedMediaSize,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        },
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    )
}

@DayNightPreview
@Composable
fun FolderItemPreview() {
    NextPlayerTheme {
        FolderItem(
            directory = Directory.sample,
            onClick = { },
            onLongClick = { }
        )
    }
}

fun Modifier.negativeVerticalPadding(vertical: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.offset(vertical = (-vertical * 2).roundToPx()))

    layout(
        width = placeable.width,
        height = placeable.height - (vertical * 2).roundToPx()
    ) { placeable.place(0, 0 - vertical.roundToPx()) }
}
