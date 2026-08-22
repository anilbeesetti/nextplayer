with open("feature/player/src/main/java/com/graviton/feature/player/ui/DecoderSelectorView.kt", "r") as f:
    content = f.read()

# Make sure it's valid Kotlin
import re
content = re.sub(r'DecoderMode\.AUTO -> "Auto"\s*DecoderMode\.HARDWARE -> "HW"\s*DecoderMode\.SOFTWARE -> "SW"',
                 'DecoderMode.AUTO -> "Auto"\n                    DecoderMode.HARDWARE -> "HW"\n                    DecoderMode.SOFTWARE -> "SW"', content)

with open("feature/player/src/main/java/com/graviton/feature/player/ui/DecoderSelectorView.kt", "w") as f:
    f.write('''package com.graviton.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.graviton.core.model.DecoderMode
import com.graviton.core.ui.R

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
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            DecoderMode.entries.forEach { mode ->
                val name = when (mode) {
                    DecoderMode.AUTO -> "Auto"
                    DecoderMode.HARDWARE -> "HW"
                    DecoderMode.SOFTWARE -> "SW"
                }

                ListItem(
                    headlineContent = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        RadioButton(
                            selected = mode == currentDecoderMode,
                            onClick = null,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDecoderModeSelected(mode)
                            onDismiss()
                        },
                )
            }
        }
    }
}
''')

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "r") as f:
    content = f.read()

content = re.sub(r'DecoderMode\.HARDWARE_PLUS -> DefaultRenderersFactory\.EXTENSION_RENDERER_MODE_OFF\s*', '', content)

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "w") as f:
    f.write(content)
