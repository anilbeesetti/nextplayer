package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@Composable
fun TextIconToggleButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
    indication: Indication? = null,
    onClick: (Boolean) -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = { onClick(!isSelected) },
            )
            .padding(10.dp),

    ) {
        FilledIconToggleButton(
            checked = isSelected,
            onCheckedChange = onClick,
            interactionSource = interactionSource,
        ) {
            Icon(imageVector = icon, contentDescription = text)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview
@Composable
fun TextIconToggleButtonPreview() {
    NextPlayerTheme {
        Surface {
            TextIconToggleButton(
                text = "Text",
                icon = Icons.Rounded.Search,
            )
        }
    }
}
