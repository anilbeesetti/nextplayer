package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferenceSlider(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
) {
    NextSegmentedListItem(
        modifier = modifier,
        onClick = {},
        onLongClick = null,
        enabled = enabled,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        supportingContent = {
            Column {
                description?.let {
                    Text(text = description)
                }
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    value = value,
                    valueRange = valueRange,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                )
            }
        },
        content = {
            Text(text = title)
        },
        trailingContent = trailingContent,
    )
}
