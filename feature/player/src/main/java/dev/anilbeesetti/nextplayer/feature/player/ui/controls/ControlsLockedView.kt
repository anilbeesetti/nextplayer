package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.extractor.metadata.Chapter
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls
import dev.anilbeesetti.nextplayer.feature.player.state.MediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.durationFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.positionFormatted

/**
 * Read-only media title shown next to the unlock button while controls are locked.
 */
@Composable
fun LockedTitleView(
    modifier: Modifier = Modifier,
    title: String,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Read-only position/duration readout and progress bar shown while controls are locked.
 * Nothing here is interactive, so playback cannot be changed by touching it.
 */
@Composable
fun LockedProgressView(
    modifier: Modifier = Modifier,
    mediaPresentationState: MediaPresentationState,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 1.dp),
        ) {
            Text(
                text = "${mediaPresentationState.positionFormatted} / ${mediaPresentationState.durationFormatted}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        LockedProgressBar(
            position = mediaPresentationState.position.toFloat(),
            duration = mediaPresentationState.duration.toFloat(),
            chapters = mediaPresentationState.chapters,
        )
    }
}

@Composable
private fun LockedProgressBar(
    modifier: Modifier = Modifier,
    position: Float,
    duration: Float,
    chapters: List<Chapter>,
) {
    val useMaterialYouControls = LocalUseMaterialYouControls.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackHeight = if (useMaterialYouControls) 8.dp else 4.dp
    val inactiveColor = if (useMaterialYouControls) primaryColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.5f)
    val playedFraction = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f

    // The seekbar is always laid out left to right, so mirror that here for RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .chapterGaps(chapters, duration)
                    .clip(CircleShape)
                    .background(inactiveColor),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(playedFraction)
                        .fillMaxHeight()
                        .background(primaryColor),
                )
            }
        }
    }
}
