package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun PreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean,
    content: @Composable () -> Unit = {},
) {
    ListItemComponent(
        leadingContent = {
            icon?.let {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary.applyAlpha(enabled),
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = LocalContentColor.current.applyAlpha(enabled),
            )
        },
        supportingContent = {
            description?.let {
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.applyAlpha(enabled),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        trailingContent = content,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectablePreference(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    ListItemComponent(
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDecoration = if (selected) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
        },
        supportingContent = {
            description?.let {
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (selected) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
            }
        },
        trailingContent = {
            Checkbox(
                modifier = Modifier.semantics { contentDescription = title },
                checked = selected,
                onCheckedChange = null,
            )
        },
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 10.dp)
            .padding(vertical = 2.dp),
    )
}

@Preview(showBackground = true)
@Composable
fun PreferenceItemPreview() {
    PreferenceItem(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap,
        enabled = true,
    )
}

@Preview(showBackground = true)
@Composable
fun SelectablePreferencePreview() {
    SelectablePreference(
        title = "Title",
        description = "Description of the preference item goes here.",
    )
}

internal fun Color.applyAlpha(enabled: Boolean): Color {
    return if (enabled) this else this.copy(alpha = 0.6f)
}
