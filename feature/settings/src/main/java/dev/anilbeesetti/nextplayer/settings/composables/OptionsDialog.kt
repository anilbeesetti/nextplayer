package dev.anilbeesetti.nextplayer.settings.composables

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog

@Composable
fun OptionsDialog(
    text: String,
    onDismissClick: () -> Unit,
    options: @Composable ColumnScope.() -> Unit
) {
    NextDialog(
        onDismissRequest = onDismissClick,
        title = {
            Text(text = text)
        },
        content = {
            Column(
                modifier = Modifier
                    .scrollable(
                        state = rememberScrollState(),
                        orientation = Orientation.Vertical
                    )
                    .selectableGroup()
            ) { options() }
        },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        confirmButton = { }
    )
}
