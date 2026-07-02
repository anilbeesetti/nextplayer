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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
    onReset: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = {},
) {
    val span = valueRange.endInclusive - valueRange.start
    val keyStep = if (span <= 5f) 0.1f else 1f

    fun nudged(direction: Int): Float {
        val raw = (value + direction * keyStep).coerceIn(valueRange.start, valueRange.endInclusive)
        return if (keyStep < 1f) (raw * 10).roundToInt() / 10f else raw.roundToInt().toFloat()
    }

    NextSegmentedListItem(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    onValueChange(nudged(direction = -1))
                    onValueChangeFinished()
                    true
                }
                Key.DirectionRight -> {
                    onValueChange(nudged(direction = 1))
                    onValueChangeFinished()
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    onReset?.let { it(); true } ?: false
                }
                else -> false
            }
        },
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
