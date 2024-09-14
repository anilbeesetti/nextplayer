package dev.anilbeesetti.nextplayer.settings.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
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
    options: LazyListScope.() -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismissClick,
        title = {
            Text(text = text)
        },
        content = {
            HorizontalDivider()
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.selectableGroup(),
                content = options,
            )
            HorizontalDivider()
        },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        confirmButton = { },
    )
}
