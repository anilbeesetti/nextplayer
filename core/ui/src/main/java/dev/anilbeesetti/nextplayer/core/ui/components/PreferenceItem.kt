package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    content: @Composable () -> Unit = {}
) {
    ListItem(
        leadingContent = {
            icon?.let {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        },
        supportingContent = {
            description?.let {
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        trailingContent = content,
        modifier = modifier.padding(vertical = 10.dp)
    )
}

@Preview
@Composable
fun PreferenceItemPreview() {
    PreferenceItem(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectablePreference(
    title: String = "",
    description: String? = null,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 16.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (icon == null) 12.dp else 0.dp)
                    .padding(end = 8.dp)
            ) {
                with(MaterialTheme) {
                    Text(
                        text = title,
                        maxLines = 1,
                        color = colorScheme.onSurface,
                        style = typography.titleMedium.copy(
                            textDecoration = if (selected) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                    description?.let {
                        Text(
                            text = it,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = typography.bodyMedium.copy(
                                textDecoration = if (selected) TextDecoration.LineThrough else TextDecoration.None
                            )
                        )
                    }
                }
            }
            Checkbox(
                modifier = Modifier.semantics { contentDescription = title },
                checked = selected,
                onCheckedChange = null
            )
        }
    }
}
