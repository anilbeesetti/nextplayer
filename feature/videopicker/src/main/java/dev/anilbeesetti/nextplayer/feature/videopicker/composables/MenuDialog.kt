package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipBorder
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.feature.videopicker.R

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MenuDialog(
    preferences: AppPreferences,
    showMenuDialog: (Boolean) -> Unit,
    update: (SortBy, SortOrder) -> Unit
) {
    Dialog(
        onDismissRequest = { showMenuDialog(false) },
        content = {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .padding(bottom = 20.dp),
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    var selectedSortBy by remember { mutableStateOf(preferences.sortBy) }
                    var selectedSortOrder by remember { mutableStateOf(preferences.sortOrder) }

                    Column {
                        Text(
                            text = stringResource(R.string.sort),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        SortOptions(
                            selectedSortBy = selectedSortBy,
                            onOptionSelected = { selectedSortBy = it }
                        )

                        SegmentedFilterChip(
                            labelOne = {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
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
                                    imageVector = Icons.Rounded.ArrowDownward,
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
                    }
                    CancelDoneButtons(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onCancelClick = {
                            showMenuDialog(false)
                        },
                        onDoneClick = {
                            update(selectedSortBy, selectedSortOrder)
                            showMenuDialog(false)
                        }
                    )
                }
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

@Composable
private fun SortOptions(
    selectedSortBy: SortBy,
    onOptionSelected: (SortBy) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        TextIconToggleButton(
            text = "Title",
            icon = Icons.Rounded.Title,
            isSelected = selectedSortBy == SortBy.TITLE,
            onClick = { onOptionSelected(SortBy.TITLE) }
        )
        TextIconToggleButton(
            text = "Duration",
            icon = Icons.Rounded.Straighten,
            isSelected = selectedSortBy == SortBy.DURATION,
            onClick = { onOptionSelected(SortBy.DURATION) }
        )
        TextIconToggleButton(
            text = "Path",
            icon = Icons.Rounded.LocationOn,
            isSelected = selectedSortBy == SortBy.PATH,
            onClick = { onOptionSelected(SortBy.PATH) }
        )
        TextIconToggleButton(
            text = "Resolution",
            icon = Icons.Rounded.HighQuality,
            isSelected = selectedSortBy == SortBy.RESOLUTION,
            onClick = { onOptionSelected(SortBy.RESOLUTION) }
        )
    }
}

@Composable
private fun CancelDoneButtons(
    modifier: Modifier = Modifier,
    onCancelClick: () -> Unit = {},
    onDoneClick: () -> Unit = {},
    arrangement: Arrangement.Horizontal = Arrangement.End
) {
    Row(
        modifier = modifier,
        horizontalArrangement = arrangement
    ) {
        TextButton(onClick = onCancelClick) {
            Text(text = stringResource(R.string.cancel))
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onDoneClick) {
            Text(text = stringResource(R.string.done))
        }
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

enum class ChipSelected {
    ONE,
    TWO
}
