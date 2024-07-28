package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSwitch
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.extensions.name

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsDialog(
    applicationPreferences: ApplicationPreferences,
    onDismiss: () -> Unit,
    updatePreferences: (ApplicationPreferences) -> Unit,
) {
    var preferences by remember { mutableStateOf(applicationPreferences) }

    NextDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.quick_settings))
        },
        content = {
            HorizontalDivider()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                DialogSectionTitle(text = "Media view mode")
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MediaViewMode.entries.forEachIndexed { index, viewMode ->
                        SegmentedButton(
                            selected = preferences.mediaViewMode == viewMode,
                            onClick = { preferences = preferences.copy(mediaViewMode = viewMode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MediaViewMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContentColor = MaterialTheme.colorScheme.primary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(text = viewMode.name())
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                DialogSectionTitle(text = stringResource(R.string.sort))
                SortOptions(
                    selectedSortBy = preferences.sortBy,
                    onOptionSelected = { preferences = preferences.copy(sortBy = it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Sort.Order.entries.forEachIndexed { index, sortOrder ->
                        SegmentedButton(
                            selected = preferences.sortOrder == sortOrder,
                            onClick = { preferences = preferences.copy(sortOrder = sortOrder) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Sort.Order.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContentColor = MaterialTheme.colorScheme.primary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            icon = {
                                Icon(
                                    imageVector = if (sortOrder == Sort.Order.ASCENDING) NextIcons.ArrowUpward else NextIcons.ArrowDownward,
                                    contentDescription = stringResource(R.string.ascending),
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                        ) {
                            Text(text = sortOrder.name(sortBy = preferences.sortBy))
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                DialogSectionTitle(text = stringResource(R.string.fields))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldChip(
                        label = stringResource(id = R.string.duration),
                        selected = preferences.showDurationField,
                        onClick = { preferences = preferences.copy(showDurationField = !preferences.showDurationField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.extension),
                        selected = preferences.showExtensionField,
                        onClick = { preferences = preferences.copy(showExtensionField = !preferences.showExtensionField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.path),
                        selected = preferences.showPathField,
                        onClick = { preferences = preferences.copy(showPathField = !preferences.showPathField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.resolution),
                        selected = preferences.showResolutionField,
                        onClick = { preferences = preferences.copy(showResolutionField = !preferences.showResolutionField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.size),
                        selected = preferences.showSizeField,
                        onClick = { preferences = preferences.copy(showSizeField = !preferences.showSizeField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.thumbnail),
                        selected = preferences.showThumbnailField,
                        onClick = { preferences = preferences.copy(showThumbnailField = !preferences.showThumbnailField) },
                    )
                }
            }
        },
        confirmButton = {
            DoneButton(
                onClick = {
                    updatePreferences(preferences)
                    onDismiss()
                },
            )
        },
        dismissButton = {
            CancelButton(onClick = onDismiss)
        },
    )
}

@Composable
fun FieldChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = NextIcons.CheckBox,
    unselectedIcon: ImageVector = NextIcons.CheckBoxOutline,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        leadingIcon = {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = "",
                modifier = Modifier.size(FilterChipDefaults.IconSize),
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderWidth = 1.dp,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    )
}

@Composable
private fun SortOptions(
    selectedSortBy: Sort.By,
    onOptionSelected: (Sort.By) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        TextIconToggleButton(
            text = stringResource(id = R.string.title),
            icon = NextIcons.Title,
            isSelected = selectedSortBy == Sort.By.TITLE,
            onClick = { onOptionSelected(Sort.By.TITLE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.duration),
            icon = NextIcons.Length,
            isSelected = selectedSortBy == Sort.By.LENGTH,
            onClick = { onOptionSelected(Sort.By.LENGTH) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.date),
            icon = NextIcons.Calendar,
            isSelected = selectedSortBy == Sort.By.DATE,
            onClick = { onOptionSelected(Sort.By.DATE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.size),
            icon = NextIcons.Size,
            isSelected = selectedSortBy == Sort.By.SIZE,
            onClick = { onOptionSelected(Sort.By.SIZE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.location),
            icon = NextIcons.Location,
            isSelected = selectedSortBy == Sort.By.PATH,
            onClick = { onOptionSelected(Sort.By.PATH) },
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
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = isChecked,
                enabled = enabled,
                onValueChange = { onClick() },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium,
        )
        NextSwitch(
            checked = isChecked,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 20.dp),
            enabled = enabled,
        )
    }
}

@Preview
@Composable
fun QuickSettingsPreview() {
    Surface {
        QuickSettingsDialog(applicationPreferences = ApplicationPreferences(), onDismiss = { }, updatePreferences = {})
    }
}
