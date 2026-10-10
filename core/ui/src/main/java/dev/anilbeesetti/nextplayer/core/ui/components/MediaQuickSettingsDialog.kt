package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun MediaQuickSettingsDialog(
    layoutMode: MediaLayoutMode,
    sort: Sort,
    sortOptions: List<Sort.By>,
    onDismiss: () -> Unit,
    onConfirm: (MediaLayoutMode, Sort) -> Unit,
    dateLabel: Int = R.string.date,
) {
    var selectedLayout by remember { mutableStateOf(layoutMode) }
    var selectedSort by remember { mutableStateOf(sort) }

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_settings)) },
        content = {
            HorizontalDivider()
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MediaLayoutAndSortSettings(
                    layoutMode = selectedLayout,
                    sort = selectedSort,
                    onLayoutModeChange = { selectedLayout = it },
                    onSortChange = { selectedSort = it },
                    sortOptions = sortOptions,
                    dateLabel = dateLabel,
                )
            }
        },
        confirmButton = {
            DoneButton(onClick = {
                onConfirm(selectedLayout, selectedSort)
                onDismiss()
            })
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLayoutAndSortSettings(
    layoutMode: MediaLayoutMode,
    sort: Sort,
    onLayoutModeChange: (MediaLayoutMode) -> Unit,
    onSortChange: (Sort) -> Unit,
    sortOptions: List<Sort.By> = listOf(Sort.By.TITLE, Sort.By.LENGTH, Sort.By.DATE, Sort.By.SIZE, Sort.By.PATH),
    dateLabel: Int = R.string.date,
) {
    SectionTitle(stringResource(R.string.media_layout))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        MediaLayoutMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = layoutMode == mode,
                onClick = { onLayoutModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, MediaLayoutMode.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(if (mode == MediaLayoutMode.LIST) R.string.list else R.string.grid))
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    SectionTitle(stringResource(R.string.sort))
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        sortOptions.forEach { by ->
            val (label, icon) = when (by) {
                Sort.By.TITLE -> R.string.title to NextIcons.Title
                Sort.By.LENGTH -> R.string.duration to NextIcons.Length
                Sort.By.DATE -> dateLabel to NextIcons.Calendar
                Sort.By.SIZE -> R.string.size to NextIcons.Size
                Sort.By.PATH -> R.string.location to NextIcons.Location
            }
            TextIconToggleButton(
                text = stringResource(label),
                icon = icon,
                isSelected = sort.by == by,
                onClick = { onSortChange(sort.copy(by = by)) },
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Sort.Order.entries.forEachIndexed { index, order ->
            SegmentedButton(
                selected = sort.order == order,
                onClick = { onSortChange(sort.copy(order = order)) },
                shape = SegmentedButtonDefaults.itemShape(index, Sort.Order.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                ),
                icon = {
                    Icon(
                        imageVector = if (order == Sort.Order.ASCENDING) NextIcons.ArrowUpward else NextIcons.ArrowDownward,
                        contentDescription = null,
                    )
                },
            ) {
                val ascending = order == Sort.Order.ASCENDING
                val label = when (sort.by) {
                    Sort.By.TITLE, Sort.By.PATH -> if (ascending) R.string.a_z else R.string.z_a
                    Sort.By.LENGTH -> if (ascending) R.string.shortest else R.string.longest
                    Sort.By.SIZE -> if (ascending) R.string.smallest else R.string.largest
                    Sort.By.DATE -> if (ascending) R.string.oldest else R.string.newest
                }
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}
