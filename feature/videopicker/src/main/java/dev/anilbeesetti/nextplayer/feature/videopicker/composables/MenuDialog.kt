package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MenuDialog(
    preferences: AppPreferences,
    showMenuDialog: (Boolean) -> Unit,
    updateSortBy: (SortBy) -> Unit
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
                    var sortBy by remember { mutableStateOf(preferences.sortBy) }

                    Column {
                        val textStyle = MaterialTheme.typography.headlineSmall
                        ProvideTextStyle(value = textStyle) {
                            Text(text = "Sort")
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            TextIconToggleButton(
                                text = "Title",
                                icon = Icons.Rounded.Title,
                                isSelected = sortBy == SortBy.TITLE,
                                onClick = { sortBy = SortBy.TITLE }
                            )
                            TextIconToggleButton(
                                text = "Duration",
                                icon = Icons.Rounded.Straighten,
                                isSelected = sortBy == SortBy.DURATION,
                                onClick = { sortBy = SortBy.DURATION }
                            )
                            TextIconToggleButton(
                                text = "Path",
                                icon = Icons.Rounded.LocationOn,
                                isSelected = sortBy == SortBy.PATH,
                                onClick = { sortBy = SortBy.PATH }
                            )
                            TextIconToggleButton(
                                text = "Resolution",
                                icon = Icons.Rounded.HighQuality,
                                isSelected = sortBy == SortBy.RESOLUTION,
                                onClick = { sortBy = SortBy.RESOLUTION }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showMenuDialog(false) }) {
                            Text(text = "CANCEL")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                showMenuDialog(false)
                                updateSortBy(sortBy)
                            }
                        ) {
                            Text(text = "DONE")
                        }
                    }
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
