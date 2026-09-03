package com.graviton.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.graviton.core.common.extensions.isTelevision
import com.graviton.core.ui.R
import com.graviton.core.ui.components.requestFocusUntilLanded
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.core.ui.theme.GravitonTheme

/**
 * The adaptive sheet every player overlay is presented in.
 *
 * It behaves like a Material 3 bottom sheet in portrait and like a side sheet in landscape, which
 * keeps the video visible while a sheet is open. The height is capped rather than fixed so short
 * sheets stay short and long ones scroll, instead of every sheet occupying the same slab of screen.
 */
@Composable
fun BoxScope.OverlayView(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val isPortrait = configuration.isPortrait
    val layoutDirection = LocalLayoutDirection.current
    val endPadding = WindowInsets.safeDrawing
        .asPaddingValues()
        .calculateEndPadding(layoutDirection)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(show) {
        if (show && isTv) {
            focusRequester.requestFocusUntilLanded(attempts = 5)
        }
    }

    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        // Sheets may never take the whole screen: the video has to stay partly visible, and an
        // unusably tall sheet on a small phone in landscape is the failure mode this guards against.
        val maxSheetHeight = maxHeight * if (isPortrait) 0.72f else 1f
        val sheetWidth = if (isPortrait) maxWidth else (maxWidth * 0.5f).coerceAtMost(480.dp)

        AnimatedVisibility(
            modifier = Modifier.align(if (isPortrait) Alignment.BottomCenter else Alignment.CenterEnd),
            visible = show,
            enter = (if (isPortrait) slideInVertically { it } else slideInHorizontally { it }) +
                fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = (if (isPortrait) slideOutVertically { it } else slideOutHorizontally { it }) +
                fadeOut(animationSpec = tween(durationMillis = 90)),
        ) {
            Surface(
                shape = if (isPortrait) {
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                } else {
                    RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 1.dp,
                modifier = modifier
                    .semantics { isTraversalGroup = true }
                    .then(
                        if (isPortrait) {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxSheetHeight)
                        } else {
                            Modifier
                                .width(sheetWidth)
                                .widthIn(min = 280.dp)
                                .fillMaxSize()
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .focusGroup()
                        .imePadding()
                        .padding(end = endPadding),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp, top = 20.dp, bottom = 4.dp)
                            .semantics { traversalIndex = -1f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (onDismiss != null) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = NextIcons.Close,
                                    contentDescription = stringResource(R.string.close_search),
                                )
                            }
                        }
                    }
                    content()
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewOverlayView() {
    GravitonTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            OverlayView(title = "Selector view", show = true) {
                Text("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Lorem ipsum")
            }
        }
    }
}

enum class OverlayView {
    AUDIO_SELECTOR,
    SUBTITLE_SELECTOR,
    PLAYBACK_SPEED,
    VIDEO_CONTENT_SCALE,
    PLAYLIST,
    DECODER_SELECTOR,
    MORE_OPTIONS,
    DISPLAY_SETTINGS,
    VIDEO_INFORMATION,
    BOOKMARKS,
    CHAPTERS,
    CUT_SEGMENT,
    TUTORIAL,
}
