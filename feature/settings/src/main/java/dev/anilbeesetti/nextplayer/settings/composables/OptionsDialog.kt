package dev.anilbeesetti.nextplayer.settings.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            Divider(modifier = Modifier.padding(bottom = 8.dp))
            Column(modifier = Modifier.selectableGroup()) {
                options()
            }
            Divider(modifier = Modifier.padding(top = 8.dp))
        },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        confirmButton = { }
    )
}
