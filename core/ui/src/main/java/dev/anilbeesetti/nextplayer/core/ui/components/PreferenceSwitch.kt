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
    index: Int = 0,
    count: Int = 1,
) {
    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        index = index,
        count = count,
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
