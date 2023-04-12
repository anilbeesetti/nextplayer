package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun ClickablePreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit = {}
) {
    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        modifier = modifier.clickable(onClick = onClick, enabled = enabled)
    )
}

@Preview
@Composable
fun ClickablePreferenceItemPreview() {
    ClickablePreferenceItem(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap,
        onClick = {}
    )
}
