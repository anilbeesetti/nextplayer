package com.graviton.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.graviton.core.model.DecoderMode
import com.graviton.core.ui.R
import com.graviton.feature.player.extensions.descriptionRes
import com.graviton.feature.player.extensions.nameRes

@Composable
fun BoxScope.DecoderSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    currentDecoderMode: DecoderMode,
    onDecoderModeSelected: (DecoderMode) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.decoder),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .padding(horizontal = 16.dp)
                .selectableGroup(),
        ) {
            DecoderMode.entries.forEach { mode ->
                RadioButtonRow(
                    selected = mode == currentDecoderMode,
                    text = stringResource(mode.nameRes()),
                    supportingText = stringResource(mode.descriptionRes()),
                    onClick = {
                        onDecoderModeSelected(mode)
                        onDismiss()
                    },
                )
            }
        }
    }
}
