package dev.anilbeesetti.nextplayer.settings.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog

@Composable
fun OptionsDialog(
    text: String,
    onDismissClick: () -> Unit,
    options: LazyListScope.() -> Unit
) {
    NextDialog(
        onDismissRequest = onDismissClick,
        title = {
            Text(text = text)
        },
        content = {
            Divider()
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.selectableGroup()
            ) { options() }
            Divider()
        },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        confirmButton = { }
    )
}

@Composable
fun InputDialog(
    title: String,
    description: String,
    onDismissClick: () -> Unit,
    onDoneClick: () -> Unit,
    content: @Composable () -> Unit
) {
    NextDialog(
        onDismissRequest = { /*TODO*/ },
        title = {
            Text(text = title)
        },
        content = {
            Text(text = description)
            Spacer(modifier = Modifier.height(20.dp))
            content()
        },
        confirmButton = { DoneButton(onClick = onDoneClick) },
        dismissButton = { CancelButton(onClick = onDismissClick) }
    )
}
