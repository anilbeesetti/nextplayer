package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun PreferenceSwitchWithDivider(
    title: String = "",
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isChecked: Boolean = true,
    onClick: (() -> Unit) = {},
    onChecked: () -> Unit = {},
) {
    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        enabled = enabled,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VerticalDivider(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
                NextSwitch(
                    checked = isChecked,
                    onCheckedChange = { onChecked() },
                    enabled = enabled,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun PreferenceSwitchWithDividerPreview() {
    PreferenceSwitchWithDivider(
        title = "Title",
        description = "Description of the preference items goes here.",
        icon = NextIcons.DoubleTap,
        onClick = {},
        onChecked = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreferenceCheckbox(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isChecked: Boolean = true,
    onClick: (() -> Unit) = {},
    onLongClick: (() -> Unit) = {},
) {
    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        modifier = Modifier
            .toggleable(
                value = isChecked,
                enabled = enabled,
                onValueChange = { onClick() },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        enabled = enabled,
        content = {
            Checkbox(
                checked = isChecked,
                onCheckedChange = null,
            )
        },
    )
}
