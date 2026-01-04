package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    index: Int = 0,
    count: Int = 1,
    trailingContent: @Composable () -> Unit = {},
) {
    NextSegmentedListItem(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        index = index,
        count = count,
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        supportingContent = description?.let {
            {
                Text(text = description)
            }
        },
        content = {
            Text(text = title)
        },
        trailingContent = trailingContent,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectablePreference(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    index: Int = 0,
    count: Int = 1,
) {
    NextSegmentedListItem(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        index = index,
        count = count,
        content = {
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
