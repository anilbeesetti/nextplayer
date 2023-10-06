package dev.anilbeesetti.nextplayer.feature.player.notification.common

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.ui.R as Media3R
import dev.anilbeesetti.nextplayer.core.ui.R as R

internal data class Action(
    @DrawableRes val iconResource: Int,
    @StringRes val titleResource: Int,
    @Player.Command val command: Int
)

@UnstableApi
internal fun Action.asNotificationAction(
    context: Context,
    mediaSession: MediaSession,
    actionFactory: MediaNotification.ActionFactory
) = actionFactory.createMediaAction(
    mediaSession,
    IconCompat.createWithResource(context, iconResource),
    context.getString(titleResource),
    command
)

@UnstableApi
@SuppressLint("PrivateResource")
internal object Actions {

    // TODO: Buttons don't Work
    internal fun getSkipPreviousAction(
        context: Context,
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory
    ) = Action(
        iconResource = R.drawable.ic_skip_prev,
        titleResource = Media3R.string.exo_controls_previous_description,
        command = Player.COMMAND_SEEK_TO_PREVIOUS
    ).asNotificationAction(context, mediaSession, actionFactory)

    internal fun getPlayPauseAction(
        context: Context,
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
        playWhenReady: Boolean
    ) = Action(
        iconResource = if (playWhenReady) R.drawable.ic_pause else R.drawable.ic_play,
        titleResource = if (playWhenReady) Media3R.string.exo_controls_pause_description else Media3R.string.exo_controls_play_description,
        command = Player.COMMAND_PLAY_PAUSE
    ).asNotificationAction(context, mediaSession, actionFactory)

    // TODO: Buttons don't Work
    internal fun getSkipNextAction(
        context: Context,
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory
    ) = Action(
        iconResource = R.drawable.ic_skip_next,
        titleResource = Media3R.string.exo_controls_next_description,
        command = Player.COMMAND_SEEK_TO_NEXT
    ).asNotificationAction(context, mediaSession, actionFactory)
}
