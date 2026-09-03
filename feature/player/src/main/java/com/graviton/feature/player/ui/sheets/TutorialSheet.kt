package com.graviton.feature.player.ui.sheets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.graviton.core.model.DoubleTapGesture
import com.graviton.core.model.PlayerPreferences
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.player.ui.OverlayView

private data class TutorialEntry(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
)

/**
 * Onboarding for the gestures the player really supports.
 *
 * The list is derived from the current [PlayerPreferences], so a gesture the user has turned off is
 * not advertised. The only motion is a small pulse on the leading icon of the row in view, which
 * stops as soon as the sheet closes.
 */
@Composable
fun BoxScope.TutorialSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    playerPreferences: PlayerPreferences,
    onDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = buildList {
        add(TutorialEntry(NextIcons.Tap, R.string.tutorial_single_tap, R.string.tutorial_single_tap_desc))
        if (playerPreferences.doubleTapGesture != DoubleTapGesture.NONE) {
            add(TutorialEntry(NextIcons.DoubleTap, R.string.tutorial_double_tap, R.string.tutorial_double_tap_desc))
        }
        if (playerPreferences.useSeekControls) {
            add(TutorialEntry(NextIcons.SwipeHorizontal, R.string.tutorial_seek, R.string.tutorial_seek_desc))
        }
        if (playerPreferences.enableVolumeSwipeGesture) {
            add(TutorialEntry(NextIcons.VolumeUp, R.string.tutorial_volume, R.string.tutorial_volume_desc))
        }
        if (playerPreferences.enableBrightnessSwipeGesture) {
            add(TutorialEntry(NextIcons.Brightness, R.string.tutorial_brightness, R.string.tutorial_brightness_desc))
        }
        if (playerPreferences.useLongPressControls) {
            add(TutorialEntry(NextIcons.Speed, R.string.tutorial_long_press, R.string.tutorial_long_press_desc))
        }
        if (playerPreferences.useZoomControls) {
            add(TutorialEntry(NextIcons.Pinch, R.string.tutorial_zoom, R.string.tutorial_zoom_desc))
        }
        add(TutorialEntry(NextIcons.Rotation, R.string.tutorial_rotate, R.string.tutorial_rotate_desc))
    }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.tutorial_title),
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            itemsIndexed(items = entries, key = { _, entry -> entry.titleRes }) { index, entry ->
                TutorialRow(entry = entry, visible = show, index = index)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDontShowAgain) {
                Text(text = stringResource(R.string.tutorial_dont_show_again))
            }
            FilledTonalButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.got_it))
            }
        }
    }
}

@Composable
private fun TutorialRow(entry: TutorialEntry, visible: Boolean, index: Int) {
    // A one-shot staggered entrance. Deliberately finite: the video keeps playing behind this
    // sheet, so nothing here is allowed to animate forever.
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 220,
            delayMillis = (index * 35).coerceAtMost(280),
        ),
        label = "tutorialRowEntrance",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(10.dp)
                    .graphicsLayer {
                        val scale = 0.85f + 0.15f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = progress
                    },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(entry.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
