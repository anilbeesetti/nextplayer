package com.graviton.core.ui.components

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R
import com.graviton.core.ui.theme.LocalGlassUi
import com.graviton.core.ui.theme.glassBorderColor
import com.graviton.core.ui.theme.glassContainerColor
import kotlinx.coroutines.delay

/** Which wait the player is in. The two are visually distinct on purpose. */
enum class BufferingIndicatorKind {
    /** The player is waiting for more of a stream it has already started. */
    BUFFERING,

    /** Nothing is playing yet: the item is still being opened. */
    INITIAL_LOADING,
}

/** A stall shorter than this never draws anything, so the indicator cannot flicker. */
private const val BUFFERING_SHOW_DELAY_MS = 180L

/** Once drawn, the indicator stays for at least this long before it is allowed to disappear. */
private const val MIN_VISIBLE_MS = 200L

/** The ring itself: large enough to read from a couch, small enough not to cover the picture. */
private val RING_SIZE = 44.dp
private val RING_STROKE = 4.dp
private val HALO_SIZE = 72.dp

/**
 * Graviton's wait indicator.
 *
 * A Material 3 [CircularProgressIndicator] on a small translucent disc. The disc — not a full
 * screen scrim — is what keeps the ring readable over a bright frame, so the video underneath is
 * never dimmed and the controls, subtitles and picture stay visible.
 *
 * The component owns its own timing so callers only have to pass player state:
 *
 *  - a buffering blip shorter than [BUFFERING_SHOW_DELAY_MS] never draws,
 *  - once drawn it stays for [MIN_VISIBLE_MS], so repeated stalls do not strobe,
 *  - an initial load appears straight away with a restrained fade ([BufferingIndicatorKind.INITIAL_LOADING]),
 *  - a mid-playback rebuffer rises with a shorter, springier transition ([BufferingIndicatorKind.BUFFERING]).
 *
 * Percentage correctness is unchanged: this component still never invents progress. [progress] must
 * be a real measurement from the playback pipeline (Graviton passes the player's buffered percentage
 * divided by 100); pass null whenever no reliable value exists and the indicator shows the label
 * alone.
 */
@Composable
fun BufferingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
    /** Real 0..1 progress from the streaming pipeline, or null when it cannot be measured. */
    progress: Float? = null,
    label: String = stringResource(R.string.state_buffering),
    kind: BufferingIndicatorKind = BufferingIndicatorKind.BUFFERING,
) {
    val shown = rememberBufferingIndicatorVisibility(visible = visible, kind = kind)
    val reduceMotion = rememberReduceMotion()
    val glass = LocalGlassUi.current

    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else if (kind == BufferingIndicatorKind.INITIAL_LOADING) {
            // Restrained: the initial load fades in rather than popping.
            fadeIn(animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing))
        } else {
            fadeIn(animationSpec = tween(durationMillis = 160)) + scaleIn(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                initialScale = 0.9f,
            )
        },
        exit = if (reduceMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(durationMillis = 160)) + scaleOut(
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                targetScale = 0.95f,
            )
        },
    ) {
        val percentage = progress?.let { (it.coerceIn(0f, 1f) * 100).toInt() }
        val description = when {
            percentage != null -> "$label $percentage%"
            else -> label
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        ) {
            Box(
                modifier = Modifier
                    .size(HALO_SIZE)
                    .background(
                        color = if (glass) glassContainerColor() else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(RING_SIZE),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        strokeWidth = RING_STROKE,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(RING_SIZE),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        strokeWidth = RING_STROKE,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            if (label.isNotEmpty() || percentage != null) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (glass) glassContainerColor() else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = if (glass) BorderStroke(1.dp, glassBorderColor()) else null,
                    tonalElevation = 0.dp,
                    shadowElevation = if (glass) 6.dp else 0.dp,
                ) {
                    Text(
                        text = if (percentage != null) "$label $percentage%" else label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Debounce and minimum-display timing for [BufferingIndicator].
 *
 * The state is derived from the caller's [visible] flag and never survives it: the indicator is
 * hidden immediately once the player reports that it is playing again, apart from the short
 * [MIN_VISIBLE_MS] floor that keeps a stall from flashing.
 */
@Composable
private fun rememberBufferingIndicatorVisibility(visible: Boolean, kind: BufferingIndicatorKind): Boolean {
    var shown by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(visible, kind) {
        if (visible) {
            // The initial load is expected to last, so it is announced at once; a rebuffer during
            // playback has to outlive the show delay before it is worth drawing.
            if (kind == BufferingIndicatorKind.BUFFERING) delay(BUFFERING_SHOW_DELAY_MS)
            shown = true
            shownAt = SystemClock.elapsedRealtime()
        } else if (shown) {
            val shownForMs = SystemClock.elapsedRealtime() - shownAt
            if (shownForMs < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - shownForMs)
            shown = false
        }
    }

    return shown
}

/**
 * True when the system asks for reduced motion (animator duration scale 0, or animations off).
 * Compose's own transitions follow the same setting, but the player's overlays are built by hand,
 * so they read it explicitly and drop their scale and slide flourishes.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        scale == 0f
    }
}
