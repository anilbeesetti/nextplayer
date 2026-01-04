package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClickablePreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    index: Int = 0,
    count: Int = 1,
) {
    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        index = index,
        count = count,
    )
}

@Preview
@Composable
private fun ClickablePreferenceItemPreview() {
    ClickablePreferenceItem(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap,
        onClick = {},
        enabled = false,
    )
}
