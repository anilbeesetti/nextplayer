package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderItem(
    folder: Folder,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.folder_thumb),
                contentDescription = "",
                tint = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .width(min(100.dp, LocalConfiguration.current.screenWidthDp.dp * 0.3f))
                    .aspectRatio(20 / 15f)
            )
        },
        headlineContent = {
            Text(
                text = folder.name,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            if (preferences.showPathField) {
                Text(
                    text = folder.path.substringBeforeLast("/"),
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
                InfoChip(
                    text = "${folder.mediaCount} " +
                        stringResource(id = R.string.video.takeIf { folder.mediaCount == 1 } ?: R.string.videos)
                )
                if (preferences.showSizeField) {
                    InfoChip(text = folder.formattedMediaSize)
                }
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderGridItem(
    folder: Folder,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val localConfig = LocalConfiguration.current

        val thumbWidth = when (preferences.thumbnailSize) {
            Size.COMPACT -> 130.dp
            Size.MEDIUM -> 165.dp
            Size.LARGE -> 200.dp
        }
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .aspectRatio(16f / 10f) ) {
            Icon(
                imageVector = NextIcons.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
            )
        }
        Text(
            text = folder.name,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium,
            overflow = TextOverflow.Ellipsis
        )

        if (preferences.showPathField) {
            Text(
                text = folder.path,
                maxLines = 1,
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
            InfoChip(text = "${folder.mediaCount} " +
                    stringResource(id = R.string.video.takeIf { folder.mediaCount == 1 }
                        ?: R.string.videos))

            if (preferences.showSizeField) {
                InfoChip(text = folder.formattedMediaSize)
            }
        }
    }
}

@DayNightPreview
@Composable
fun FolderItemPreview() {
    NextPlayerTheme {
        FolderItem(folder = Folder.sample, preferences = ApplicationPreferences())
    }
}

@DayNightPreview
@Composable
fun FolderGridItemPreview() {
    NextPlayerTheme {
        FolderGridItem(folder = Folder.sample, preferences = ApplicationPreferences())
    }
}


