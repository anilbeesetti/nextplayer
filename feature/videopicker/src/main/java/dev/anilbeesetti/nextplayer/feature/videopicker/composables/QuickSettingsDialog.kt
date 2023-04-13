package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSwitch
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    updatePreferences: (SortBy, SortOrder, Boolean) -> Unit
) {
    var selectedSortBy by remember { mutableStateOf(preferences.sortBy) }
    var selectedSortOrder by remember { mutableStateOf(preferences.sortOrder) }
    var groupVideos by remember { mutableStateOf(preferences.groupVideosByFolder) }

    NextDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.quick_settings))
        },
        content = {
            Divider()
            DialogSectionTitle(text = stringResource(R.string.sort))
            SortOptions(
                selectedSortBy = selectedSortBy,
                onOptionSelected = { selectedSortBy = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SegmentedFilterChip(
                labelOne = {
                    Icon(
                        imageVector = NextIcons.ArrowUpward,
                        contentDescription = stringResource(R.string.ascending)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ascending),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                labelTwo = {
                    Icon(
                        imageVector = NextIcons.ArrowDownward,
                        contentDescription = stringResource(R.string.descending)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.descending),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                selected = if (selectedSortOrder == SortOrder.ASCENDING) ChipSelected.ONE else ChipSelected.TWO,
                onClick = {
                    selectedSortOrder = when (it) {
                        ChipSelected.ONE -> SortOrder.ASCENDING
                        ChipSelected.TWO -> SortOrder.DESCENDING
                    }
                }
            )
            Divider(modifier = Modifier.padding(top = 16.dp))
            DialogPreferenceSwitch(
                text = stringResource(id = R.string.group_videos),
                isChecked = groupVideos,
                onClick = { groupVideos = !groupVideos }
            )
        },
        confirmButton = {
            DoneButton(
                onClick = {
                    updatePreferences(selectedSortBy, selectedSortOrder, groupVideos)
                    onDismiss()
                }
            )
        },
        dismissButton = {
            CancelButton(onClick = onDismiss)
        }
    )
}

@Composable
private fun SortOptions(
    selectedSortBy: SortBy,
    onOptionSelected: (SortBy) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
    ) {
        TextIconToggleButton(
            text = "Title",
            icon = NextIcons.Title,
            isSelected = selectedSortBy == SortBy.TITLE,
            onClick = { onOptionSelected(SortBy.TITLE) }
        )
        TextIconToggleButton(
            text = "Duration",
            icon = NextIcons.Length,
            isSelected = selectedSortBy == SortBy.DURATION,
            onClick = { onOptionSelected(SortBy.DURATION) }
        )
        TextIconToggleButton(
            text = "Path",
            icon = NextIcons.Location,
            isSelected = selectedSortBy == SortBy.PATH,
            onClick = { onOptionSelected(SortBy.PATH) }
        )
        TextIconToggleButton(
            text = "Resolution",
            icon = NextIcons.HighQuality,
            isSelected = selectedSortBy == SortBy.RESOLUTION,
            onClick = { onOptionSelected(SortBy.RESOLUTION) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedFilterChip(
    labelOne: @Composable RowScope.() -> Unit,
    labelTwo: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    border: SelectableChipBorder = FilterChipDefaults.filterChipBorder(
        selectedBorderColor = MaterialTheme.colorScheme.primary,
        borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        borderWidth = 1.dp,
        selectedBorderWidth = 1.dp
    ),
    selected: ChipSelected,
    onClick: (ChipSelected) -> Unit = { }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        FilterChip(
            modifier = Modifier.weight(1f),
            onClick = { onClick(ChipSelected.ONE) },
            shape = RoundedCornerShape(
                topStart = 8.dp,
                bottomStart = 8.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp
            ),
            label = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    labelOne()
                }
            },
            border = border,
            selected = selected == ChipSelected.ONE
        )
        FilterChip(
            modifier = Modifier
                .weight(1f),
            onClick = { onClick(ChipSelected.TWO) },
            shape = RoundedCornerShape(
                topStart = 0.dp,
                bottomStart = 0.dp,
                topEnd = 8.dp,
                bottomEnd = 8.dp
            ),
            label = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    labelTwo()
                }
            },
            border = border,
            selected = selected == ChipSelected.TWO
        )
    }
}

@Composable
private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun DialogPreferenceSwitch(
    text: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = isChecked,
                enabled = enabled,
                onValueChange = { onClick() }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium
        )
        NextSwitch(
            checked = isChecked,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 20.dp),
            enabled = enabled,
        )
    }
}

enum class ChipSelected {
    ONE,
    TWO
}
