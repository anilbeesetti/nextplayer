package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ListItemComponent
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderItem(
    folder: Folder,
    isRecentlyPlayedFolder: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
) {
    ListItemComponent(
        colors = ListItemDefaults.colors(
            headlineColor = if (isRecentlyPlayedFolder && preferences.markLastPlayedMedia) {
                MaterialTheme.colorScheme.primary
            } else {
                ListItemDefaults.colors().headlineColor
            },
            supportingColor = if (isRecentlyPlayedFolder && preferences.markLastPlayedMedia) {
                MaterialTheme.colorScheme.primary
            } else {
                ListItemDefaults.colors().supportingTextColor
            },
        ),
        leadingContent = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.folder_thumb),
                contentDescription = "",
                tint = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .width(min(100.dp, LocalConfiguration.current.screenWidthDp.dp * 0.3f))
                    .aspectRatio(20 / 15f),
            )
        },
        headlineContent = {
            Text(
                text = folder.name,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            if (preferences.showPathField) {
                Text(
                    text = folder.path.substringBeforeLast("/"),
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
                InfoChip(
                    text = "${folder.mediaCount} " +
                        stringResource(id = R.string.video.takeIf { folder.mediaCount == 1 } ?: R.string.videos),
                )
                if (preferences.showSizeField) {
                    InfoChip(text = folder.formattedMediaSize)
                }
            }
        },
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
fun FolderItemRecentlyPlayedPreview() {
    NextPlayerTheme {
        FolderItem(
            folder = Folder.sample,
            preferences = ApplicationPreferences(),
            isRecentlyPlayedFolder = true,
        )
    }
}

@PreviewLightDark
@Composable
fun FolderItemPreview() {
    NextPlayerTheme {
        FolderItem(
            folder = Folder.sample,
            preferences = ApplicationPreferences(),
            isRecentlyPlayedFolder = false,
        )
    }
}
