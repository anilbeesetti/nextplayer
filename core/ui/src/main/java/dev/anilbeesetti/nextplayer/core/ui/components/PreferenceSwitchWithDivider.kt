package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    checkedIcon: ImageVector = NextIcons.Check,
    onClick: (() -> Unit) = {},
    onChecked: () -> Unit = {}
) {
    val thumbContent: (@Composable () -> Unit)? = if (isChecked) {
        {
            Icon(
                imageVector = checkedIcon,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    } else {
        null
    }

    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = onClick
        ),
        content = {
            Divider(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .width(1f.dp)
                    .align(Alignment.CenterVertically),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Switch(
                checked = isChecked,
                onCheckedChange = { onChecked() },
                modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                enabled = enabled,
                thumbContent = thumbContent
            )
        }
    )
}


@Preview
@Composable
fun PreferenceSwitchWithDividerPreview() {
    PreferenceSwitchWithDivider(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap,
        onClick = {},
        onChecked = {}
    )
}