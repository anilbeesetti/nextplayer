package dev.anilbeesetti.nextplayer.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers.IO
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers.Main
import dev.anilbeesetti.nextplayer.core.notification.common.Actions
import dev.anilbeesetti.nextplayer.core.ui.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@UnstableApi
class NextNotificationProvider @Inject constructor(
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : MediaNotification.Provider {
    private val notificationManager = checkNotNull(context.getSystemService<NotificationManager>())
    private val coroutineScope = CoroutineScope(mainDispatcher + SupervisorJob())

    override fun createNotification(mediaSession: MediaSession, customLayout: ImmutableList<CommandButton>, actionFactory: MediaNotification.ActionFactory, onNotificationChangedCallback: MediaNotification.Provider.Callback): MediaNotification {
        ensureNotificationChannel()

        val player = mediaSession.player
        val metadata = player.mediaMetadata

        val builder = NotificationCompat.Builder(context, NextNotificationChannelId)
            .setContentTitle(metadata.title)
            .setSmallIcon(R.drawable.app_icon)
            .setStyle(MediaStyle(mediaSession))
            .setContentIntent(mediaSession.sessionActivity)

        listOf(
            Actions.getSkipPreviousAction(context, mediaSession, actionFactory),
            Actions.getPlayPauseAction(context, mediaSession, actionFactory, player.playWhenReady),
            Actions.getSkipNextAction(context, mediaSession, actionFactory)
        ).forEach(builder::addAction)

        setupArtwork(
            uri = metadata.artworkUri,
            setLargeIcon = builder::setLargeIcon,
            updateNotification = {
                val notification = MediaNotification(NextNotificationId, builder.build())
                onNotificationChangedCallback.onNotificationChanged(notification)
            }
        )

        return MediaNotification(NextNotificationId, builder.build())
    }

    fun cancelCoroutineScope() = coroutineScope.cancel()

    private fun setupArtwork(
        uri: Uri?,
        setLargeIcon: (Bitmap?) -> Unit,
        updateNotification: () -> Unit
    ) = coroutineScope.launch {
        withContext(ioDispatcher) {
            if (uri == null) {
                setLargeIcon(null)
                updateNotification()
                return@withContext
            }

            val loader = ImageLoader.Builder(context)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .build()
            val request = ImageRequest.Builder(context)
                .data(uri)
                .videoFramePercent(0.25)
                .placeholder(R.drawable.cover_photo)
                .error(R.drawable.cover_photo)
                .build()
            val bitmap = loader.execute(request).drawable?.toBitmap()

            setLargeIcon(bitmap)
            updateNotification()
        }
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
        return false
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || notificationManager.getNotificationChannel(NextNotificationChannelId) != null) {
            return
        }

        val notificationChannel = NotificationChannel(
            NextNotificationChannelId,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )

        notificationManager.createNotificationChannel(notificationChannel)
    }
}

private const val NextNotificationId = 1001
private const val NextNotificationChannelId = "NextNotificationChannel"
