package dev.anilbeesetti.nextplayer.feature.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.doOnPreDraw
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import dev.anilbeesetti.nextplayer.core.model.Font
import dev.anilbeesetti.nextplayer.feature.player.extensions.toTypeface
import dev.anilbeesetti.nextplayer.feature.player.state.CuesState
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitlePositionState

@OptIn(UnstableApi::class)
@Composable
fun SubtitleView(
    modifier: Modifier = Modifier,
    cuesState: CuesState,
    positionState: SubtitlePositionState,
    isInPictureInPictureMode: Boolean,
    configuration: SubtitleConfiguration,
) {
    // Playback keeps going while the subtitle is moved; the text on screen stays put until release.
    var frozenCues by remember { mutableStateOf<List<Cue>?>(null) }
    LaunchedEffect(positionState.isHighlighted) {
        frozenCues = cuesState.currentCues?.cues.takeIf { positionState.isHighlighted }
    }
    val displayedCues = frozenCues.takeIf { positionState.isHighlighted }
        ?: cuesState.currentCues?.cues
    DisposableEffect(positionState) {
        onDispose { positionState.textBounds = null }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = if (isInPictureInPictureMode) 0f else positionState.translationYPx
            },
        factory = { context ->
            SubtitleView(context).apply {
                setStyle(
                    createCaptionStyle(
                        context,
                        configuration,
                        highlighted = false,
                    ),
                )
                if (!configuration.useSystemCaptionStyle) {
                    setFixedTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        configuration.textSize.toFloat(),
                    )
                }
                setApplyEmbeddedStyles(configuration.applyEmbeddedStyles)
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    positionState.textBounds = measureDrawnBounds(view)
                }
            }
        },
        update = { subtitleView ->
            subtitleView.setStyle(
                createCaptionStyle(
                    context = subtitleView.context,
                    configuration = configuration,
                    highlighted = positionState.isHighlighted,
                ),
            )
            subtitleView.setApplyEmbeddedStyles(configuration.applyEmbeddedStyles)
            subtitleView.setCues(displayedCues)
            if (isInPictureInPictureMode) {
                subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
            } else {
                subtitleView.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    configuration.textSize.toFloat(),
                )
            }
            subtitleView.doOnPreDraw { positionState.textBounds = measureDrawnBounds(it) }
        },
    )
}

// The subtitle view draws nothing but text, so whatever it paints is where the text is. Media3 does
// not expose the text bounds, so paint into a reduced bitmap and find the painted pixels.
private const val BOUNDS_SCALE = 3
private const val BOUNDS_MIN_ALPHA = 16

private fun measureDrawnBounds(view: View): Rect? {
    val width = view.width / BOUNDS_SCALE
    val height = view.height / BOUNDS_SCALE
    if (width == 0 || height == 0) return null

    val bitmap = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.scale(
        1f / BOUNDS_SCALE,
        1f / BOUNDS_SCALE,
    )
    view.draw(canvas)
    val pixels = IntArray(width * height)
    bitmap.getPixels(
        pixels,
        0,
        width,
        0,
        0,
        width,
        height,
    )
    bitmap.recycle()

    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((pixels[y * width + x] ushr 24) < BOUNDS_MIN_ALPHA) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    if (right < 0) return null
    return Rect(
        left = left.toFloat() * BOUNDS_SCALE,
        top = top.toFloat() * BOUNDS_SCALE,
        right = (right + 1).toFloat() * BOUNDS_SCALE,
        bottom = (bottom + 1).toFloat() * BOUNDS_SCALE,
    )
}

// Translucent grey box behind the text while the subtitle is being moved.
private val DRAG_HIGHLIGHT_COLOR = 0x80FFFFFF.toInt()

@OptIn(UnstableApi::class)
private fun createCaptionStyle(
    context: Context,
    configuration: SubtitleConfiguration,
    highlighted: Boolean,
): CaptionStyleCompat {
    val captioningManager = getSystemService(
        context,
        CaptioningManager::class.java,
    )
    val style = if (configuration.useSystemCaptionStyle && captioningManager != null) {
        CaptionStyleCompat.createFromCaptionStyle(captioningManager.userStyle)
    } else {
        CaptionStyleCompat(
            android.graphics.Color.WHITE,
            android.graphics.Color.BLACK.takeIf { configuration.showBackground }
                ?: android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            android.graphics.Color.BLACK,
            Typeface.create(
                configuration.font.toTypeface(),
                Typeface.BOLD.takeIf { configuration.textBold } ?: Typeface.NORMAL,
            ),
        )
    }
    if (!highlighted) return style
    return CaptionStyleCompat(
        style.foregroundColor,
        DRAG_HIGHLIGHT_COLOR,
        style.windowColor,
        style.edgeType,
        style.edgeColor,
        style.typeface,
    )
}

@Stable
data class SubtitleConfiguration(
    val useSystemCaptionStyle: Boolean,
    val showBackground: Boolean,
    val font: Font,
    val textSize: Int,
    val textBold: Boolean,
    val applyEmbeddedStyles: Boolean,
)
