package com.graviton.feature.music

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.music.artwork.MediaArtwork
import com.graviton.feature.music.player.MusicPlayerActivity
import com.graviton.feature.player.PlayerActivity
import com.graviton.feature.player.utils.PlayerApi
import kotlinx.coroutines.launch

/**
 * Whether the compact now-playing bar is currently hidden.
 *
 * Hiding the bar is a UI decision only: the session, the queue and the playback position are not
 * touched, and [show] brings exactly the same bar back. The state outlives navigation because it is
 * remembered by the caller (`ResponsiveNavigationSceneDecorator`), not by the bar itself.
 */
@Stable
class MiniPlayerVisibilityState {
    var isDismissed: Boolean by mutableStateOf(false)
        private set

    private var dismissedMediaId: String? = null

    /** Hides the bar. [mediaId] is remembered so a different item can bring it back on its own. */
    fun dismiss(mediaId: String?) {
        dismissedMediaId = mediaId
        isDismissed = true
    }

    /** Brings the bar back, whatever is playing. */
    fun show() {
        dismissedMediaId = null
        isDismissed = false
    }

    /**
     * Re-opens the bar when the session moves on to another item: the user put one item's bar away,
     * not every future one.
     */
    fun syncWithMedia(mediaId: String?) {
        if (!isDismissed) return
        if (mediaId != dismissedMediaId) show()
    }
}

@Composable
fun rememberMiniPlayerVisibilityState(): MiniPlayerVisibilityState =
    remember { MiniPlayerVisibilityState() }

/**
 * The compact now-playing bar.
 *
 * It is designed to sit directly on top of the [androidx.compose.material3.NavigationBar]: the
 * bottom corners are square and it carries no outer margin, so bar and nav read as a single surface
 * stack rather than a floating card.
 *
 * Dismissal is available two ways: the trailing close button, and a downward swipe. The swipe
 * follows the finger, fades the bar as it travels and either closes it past a distance/velocity
 * threshold or springs it back. Both paths call [MiniPlayerVisibilityState.dismiss], which hides the
 * UI and nothing else — playback, queue and position are owned by the session and are left alone.
 */
@Composable
fun NowPlayingMiniBar(
    modifier: Modifier = Modifier,
    visibilityState: MiniPlayerVisibilityState = rememberMiniPlayerVisibilityState(),
) {
    val connection = rememberMusicSession()
    val controller = connection.controller
    val snapshot = rememberMusicPlaybackSnapshot(controller)
    val context = LocalContext.current

    LaunchedEffect(snapshot?.mediaId) {
        visibilityState.syncWithMedia(snapshot?.mediaId)
    }

    AnimatedVisibility(
        visible = snapshot?.mediaId != null && !visibilityState.isDismissed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        // The snapshot is captured so the exit animation still has content to draw.
        val current = snapshot ?: return@AnimatedVisibility
        val title = current.title.ifBlank { stringResource(R.string.now_playing) }

        MiniPlayerSwipeToDismiss(
            onDismiss = { visibilityState.dismiss(current.mediaId) },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.mini_player_expand)) {
                        if (current.isMusic) {
                            context.startActivity(Intent(context, MusicPlayerActivity::class.java))
                        } else {
                            context.startActivity(
                                Intent(context, PlayerActivity::class.java).apply {
                                    action = Intent.ACTION_VIEW
                                    data = current.mediaId?.toUri()
                                    putExtra(PlayerApi.API_KEEP_SESSION, true)
                                },
                            )
                        }
                    },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaArtwork(
                            artworkUri = current.artworkUri?.toString(),
                            mediaUri = current.mediaId,
                            artworkData = current.artworkData,
                            modifier = Modifier.size(44.dp),
                            fallback = if (current.isMusic) NextIcons.Audio else NextIcons.Movie,
                            corner = 10.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        // The row itself is the "open now playing" target, so the text is folded
                        // into that one announcement instead of being read as separate nodes.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clearAndSetSemantics { },
                        ) {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (current.artist.isNotBlank()) {
                                Text(
                                    text = current.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (current.isMusic) {
                            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                                Icon(
                                    imageVector = NextIcons.SkipPrevious,
                                    contentDescription = stringResource(R.string.previous),
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = {
                                if (controller?.isPlaying == true) controller.pause() else controller?.play()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = if (current.isPlaying) NextIcons.Pause else NextIcons.Play,
                                contentDescription = stringResource(
                                    if (current.isPlaying) R.string.pause else R.string.play,
                                ),
                            )
                        }
                        if (current.isMusic) {
                            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                                Icon(
                                    imageVector = NextIcons.SkipNext,
                                    contentDescription = stringResource(R.string.next),
                                )
                            }
                        }
                        // Always present and always the same size: the mini player is the only place
                        // this action exists, so it must not be a swipe-only secret.
                        IconButton(onClick = { visibilityState.dismiss(current.mediaId) }) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(R.string.mini_player_dismiss),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (current.durationMs > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clearAndSetSemantics { },
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        }
    }
}

/**
 * The reopen affordance for a hidden mini player: a small Material 3 FAB that only appears while
 * something is playing and the bar is out of the way.
 */
@Composable
fun MiniPlayerReopenButton(
    modifier: Modifier = Modifier,
    visibilityState: MiniPlayerVisibilityState = rememberMiniPlayerVisibilityState(),
) {
    val connection = rememberMusicSession()
    val snapshot = rememberMusicPlaybackSnapshot(connection.controller)

    AnimatedVisibility(
        visible = visibilityState.isDismissed && snapshot?.mediaId != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        SmallFloatingActionButton(
            onClick = { visibilityState.show() },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = if (snapshot?.isMusic == true) NextIcons.Audio else NextIcons.Movie,
                contentDescription = stringResource(R.string.mini_player_reopen),
            )
        }
    }
}

/** Fraction of the bar's height that has to be pulled down before a release dismisses it. */
private const val DISMISS_DISTANCE_FRACTION = 0.42f

/** A flick faster than this dismisses the bar even without covering the full distance. */
private const val DISMISS_VELOCITY_DP_PER_SECOND = 900f

/** A release this far down still counts as a flick, so a stray tap cannot dismiss the bar. */
private const val FLICK_MIN_TRAVEL_FRACTION = 0.10f

/**
 * Vertical swipe-to-dismiss for the mini player.
 *
 * The gesture only claims the pointer after a downward touch slop, so taps reach the row's buttons
 * and horizontal drags remain available to anything underneath. While the finger is down the content
 * follows it one-to-one and fades with the distance travelled; on release it either springs back to
 * rest or is dismissed, depending on how far and how fast the finger moved. Upward drags are never
 * consumed, so pulling up still scrolls the content behind the bar.
 */
@Composable
private fun MiniPlayerSwipeToDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val velocityThreshold = with(LocalDensity.current) {
        DISMISS_VELOCITY_DP_PER_SECOND.dp.toPx()
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = offsetY.value
                // Subtle fading: the bar is fully opaque until it starts travelling, and it never
                // fades so far that it disappears before the dismissal animation runs.
                val travelled = if (size.height > 0f) offsetY.value / size.height else 0f
                alpha = (1f - travelled * 0.55f).coerceIn(0.45f, 1f)
            }
            .pointerInput(onDismiss) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    var travelled = 0f
                    var claimed = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dragY = change.positionChange().y
                        tracker.addPosition(change.uptimeMillis, change.position)

                        if (!claimed) {
                            travelled += dragY
                            when {
                                travelled >= viewConfiguration.touchSlop -> claimed = true
                                // Upwards is somebody else's gesture.
                                travelled <= -viewConfiguration.touchSlop -> break
                            }
                        }

                        if (claimed) {
                            change.consume()
                            offsetY.snapTo((offsetY.value + dragY).coerceAtLeast(0f))
                        }
                        if (!change.pressed) break
                    }

                    if (claimed) {
                        val heightPx = size.height.toFloat()
                        val velocity = tracker.calculateVelocity().y
                        val pulledFarEnough = offsetY.value > heightPx * DISMISS_DISTANCE_FRACTION
                        val flickedFastEnough = velocity > velocityThreshold &&
                            offsetY.value > heightPx * FLICK_MIN_TRAVEL_FRACTION

                        if (pulledFarEnough || flickedFastEnough) {
                            onDismiss()
                            // The bar is animated out by the caller; resetting here keeps the next
                            // time it is opened free of a stale offset.
                            offsetY.snapTo(0f)
                        } else {
                            scope.launch {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
    ) {
        content()
    }
}
