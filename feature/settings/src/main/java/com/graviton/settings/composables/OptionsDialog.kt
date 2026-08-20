package com.graviton.settings.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.components.CancelButton
import com.graviton.core.ui.components.NextDialog
import com.graviton.settings.utils.rememberTvListFocusRequester
import com.graviton.settings.utils.tvListFocus

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
                modifier = Modifier
                    .selectableGroup()
                    .tvListFocus(rememberTvListFocusRequester()),
                content = options,
            )
            HorizontalDivider()
        },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        confirmButton = { },
    )
}
