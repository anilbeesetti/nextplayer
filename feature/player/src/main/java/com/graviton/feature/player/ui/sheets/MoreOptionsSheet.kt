package com.graviton.feature.player.ui.sheets

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.player.ui.OverlayView

/**
 * The three-dot overflow of the video player.
 *
 * Every entry here routes to a real destination. Actions that already have a dedicated control in
 * the top or bottom bar (audio track, subtitle track, decoder, playback speed, lock, PiP, rotate)
 * are deliberately absent so the same action never appears twice.
 */
@Composable
fun BoxScope.MoreOptionsSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    aspectRatioLabel: String,
    longPressSpeedLabel: String,
    bookmarkCountLabel: String,
    chapterCountLabel: String,
    cutSegmentLabel: String,
    onAspectRatioClick: () -> Unit,
    onLongPressSpeedClick: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onNetworkStreamClick: () -> Unit,
    onInformationClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onChapterClick: () -> Unit,
    onCutClick: () -> Unit,
    onTutorialClick: () -> Unit,
    onShareClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.more_options),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SheetSectionTitle(text = stringResource(R.string.player_section_playback))
            SheetActionRow(
                icon = NextIcons.Frame,
                title = stringResource(R.string.aspect_ratio),
                trailingText = aspectRatioLabel,
                onClick = onAspectRatioClick,
            )
            SheetActionRow(
                icon = NextIcons.Speed,
                title = stringResource(R.string.long_press_speed),
                supportingText = stringResource(R.string.long_press_speed_desc),
                trailingText = longPressSpeedLabel,
                onClick = onLongPressSpeedClick,
            )
            SheetActionRow(
                icon = NextIcons.Display,
                title = stringResource(R.string.display_settings),
                supportingText = stringResource(R.string.display_settings_desc),
                onClick = onDisplaySettingsClick,
            )

            SheetSectionTitle(text = stringResource(R.string.player_section_media))
            SheetActionRow(
                icon = NextIcons.Playlist,
                title = stringResource(R.string.now_playing),
                supportingText = stringResource(R.string.playlist_desc),
                onClick = onPlaylistClick,
            )
            SheetActionRow(
                icon = NextIcons.Network,
                title = stringResource(R.string.network_stream),
                supportingText = stringResource(R.string.network_stream_desc),
                onClick = onNetworkStreamClick,
            )
            SheetActionRow(
                icon = NextIcons.Info,
                title = stringResource(R.string.video_information),
                supportingText = stringResource(R.string.video_information_desc),
                onClick = onInformationClick,
            )

            SheetSectionTitle(text = stringResource(R.string.player_section_tools))
            SheetActionRow(
                icon = NextIcons.Bookmark,
                title = stringResource(R.string.bookmarks),
                supportingText = stringResource(R.string.bookmarks_desc),
                trailingText = bookmarkCountLabel,
                onClick = onBookmarkClick,
            )
            SheetActionRow(
                icon = NextIcons.Chapter,
                title = stringResource(R.string.chapters),
                supportingText = stringResource(R.string.chapters_desc),
                trailingText = chapterCountLabel,
                onClick = onChapterClick,
            )
            SheetActionRow(
                icon = NextIcons.Cut,
                title = stringResource(R.string.cut),
                supportingText = stringResource(R.string.cut_desc),
                trailingText = cutSegmentLabel,
                onClick = onCutClick,
            )
            SheetActionRow(
                icon = NextIcons.Tutorial,
                title = stringResource(R.string.tutorial),
                supportingText = stringResource(R.string.tutorial_desc),
                onClick = onTutorialClick,
            )

            SheetSectionTitle(text = stringResource(R.string.player_section_other))
            SheetActionRow(
                icon = NextIcons.Share,
                title = stringResource(R.string.share),
                supportingText = stringResource(R.string.share_video_desc),
                onClick = onShareClick,
            )
            SheetActionRow(
                icon = NextIcons.Settings,
                title = stringResource(R.string.more_settings),
                supportingText = stringResource(R.string.more_settings_desc),
                onClick = onSettingsClick,
            )
        }
    }
}
