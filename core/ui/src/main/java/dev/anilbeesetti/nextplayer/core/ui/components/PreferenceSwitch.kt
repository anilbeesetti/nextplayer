package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun PreferenceSwitch(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isChecked: Boolean = true,
    onClick: (() -> Unit) = {},
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
) {
    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        trailingContent = {
            NextSwitch(
                checked = isChecked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

@Preview
@Composable
fun PreferenceSwitchPreview() {
    PreferenceSwitch(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap,
        onClick = {},
    )
}
