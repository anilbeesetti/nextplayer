package dev.anilbeesetti.nextplayer.feature.player.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing

@Composable
internal fun PlaybackDismissUndoBar(
    deadline: Long,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val remaining =
        remember(deadline) { (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
    val progress = remember(deadline) {
        Animatable(
            (remaining / 4_000f).coerceIn(
                0f,
                1f,
            ),
        )
    }
    LaunchedEffect(deadline) {
        progress.animateTo(
            0f,
            tween(
                remaining.toInt(),
                easing = LinearEasing,
            ),
        )
    }
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = .22f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Surface(
        modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("playback_undo_pill")
            .semantics {
                if (enabled) liveRegion = LiveRegionMode.Polite else hideFromAccessibility()
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Box(propagateMinConstraints = true) {
            Row(
                Modifier.fillMaxWidth().heightIn(
                    min = 64.dp,
                ).padding(
                    horizontal = 8.dp,
                    vertical = 8.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.playback_dismissed),
                    Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = { if (enabled) onUndo() },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.height(40.dp).tvFocusRing(CircleShape),
                ) {
                    Text(
                        stringResource(R.string.undo),
                        maxLines = 1,
                    )
                }
                FilledIconButton(
                    onClick = { if (enabled) onClose() },
                    enabled = enabled,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.size(40.dp).tvFocusRing(CircleShape),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(R.string.close),
                    )
                }
            }
            Canvas(Modifier.matchParentSize().testTag("playback_undo_countdown")) {
                val width = size.width * progress.value
                drawRoundRect(
                    fill,
                    topLeft = Offset(
                        if (rtl) size.width - width else 0f,
                        0f,
                    ),
                    size = Size(
                        width,
                        size.height,
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
        }
    }
}
