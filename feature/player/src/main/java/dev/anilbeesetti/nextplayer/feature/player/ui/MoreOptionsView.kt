package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.MediaInfoContent
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

private enum class MoreOption(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val overlay: OverlayView,
) {
    PLAYLIST(
        R.string.now_playing,
        R.drawable.ic_playlist,
        OverlayView.PLAYLIST,
    ),
    AUDIO(
        R.string.audio_track,
        R.drawable.ic_audio_track,
        OverlayView.AUDIO_SELECTOR,
    ),
    SUBTITLE(
        R.string.subtitle_track,
        R.drawable.ic_subtitle_track,
        OverlayView.SUBTITLE_SELECTOR,
    ),
}

@Composable
fun BoxScope.MoreOptionsView(
    modifier: Modifier = Modifier,
    show: Boolean,
    inlineActionCount: Int,
    mediaInfo: MediaInfo?,
    onOptionClick: (OverlayView) -> Unit,
    onShare: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onInfo: (() -> Unit)?,
    onDismissInfo: () -> Unit,
) {
    val buttonsFocusRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    val showingInfo = mediaInfo != null
    LaunchedEffect(
        show,
        showingInfo,
        inlineActionCount,
    ) {
        if (show && !showingInfo && inputModeManager.inputMode == InputMode.Keyboard) {
            buttonsFocusRequester.requestFocusUntilLanded()
        }
    }
    OverlayView(
        modifier = modifier,
        show = show,
        title = null,
    ) {
        AnimatedContent(
            targetState = mediaInfo,
            contentKey = { it != null },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            transitionSpec = {
                val direction = if (targetState != null) {
                    SlideDirection.Start
                } else {
                    SlideDirection.End
                }
                val animation = tween<IntOffset>(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing,
                )
                slideIntoContainer(
                    towards = direction,
                    animationSpec = animation,
                ) togetherWith slideOutOfContainer(
                    towards = direction,
                    animationSpec = animation,
                )
            },
            label = "More options",
        ) { info ->
            Column(modifier = Modifier.fillMaxSize()) {
                if (info != null) {
                    OverlayTitle(
                        title = info.video.displayName,
                        onBack = onDismissInfo,
                    )
                    MediaInfoContent(
                        mediaInfo = info,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    )
                } else {
                    OverlayTitle(title = stringResource(R.string.more))
                    MoreOptionsButtons(
                        modifier = Modifier.focusRequester(buttonsFocusRequester).focusGroup(),
                        inlineActionCount = inlineActionCount,
                        onOptionClick = onOptionClick,
                        onShare = onShare,
                        onRename = onRename,
                        onInfo = onInfo,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreOptionsButtons(
    modifier: Modifier,
    inlineActionCount: Int,
    onOptionClick: (OverlayView) -> Unit,
    onShare: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onInfo: (() -> Unit)?,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
        maxItemsInEachRow = 4,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (option in MoreOption.entries.drop(inlineActionCount)) {
            key(option) {
                MoreOptionButton(
                    label = stringResource(option.labelRes),
                    painter = painterResource(option.iconRes),
                    onClick = { onOptionClick(option.overlay) },
                )
            }
        }
        MoreOptionButton(
            label = stringResource(R.string.share),
            painter = rememberVectorPainter(NextIcons.Share),
            onClick = onShare,
        )
        MoreOptionButton(
            label = stringResource(R.string.rename),
            painter = rememberVectorPainter(NextIcons.Edit),
            onClick = onRename,
        )
        MoreOptionButton(
            label = stringResource(R.string.info),
            painter = rememberVectorPainter(NextIcons.Info),
            onClick = onInfo,
        )
    }
}

@Composable
private fun MoreOptionButton(label: String, painter: Painter, onClick: (() -> Unit)?) {
    val width = 72.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(
        modifier = Modifier.padding(horizontal = 4.dp).width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(
            modifier = Modifier.tvFocusRing(),
            enabled = onClick != null,
            onClick = { onClick?.invoke() },
        ) {
            Icon(
                painter = painter,
                contentDescription = label,
            )
        }
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}
