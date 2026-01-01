package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun PlayerButton(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    disabledContainerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    isEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                role = Role.Button,
                enabled = isEnabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        color = if (isEnabled) containerColor else disabledContainerColor,
        contentColor = if (isEnabled) contentColor else disabledContentColor,
    ) {
        Box(
            modifier = modifier.padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
