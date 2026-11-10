package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import dev.anilbeesetti.nextplayer.core.common.Logger
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.playbackTransferState

@UnstableApi
internal class PlaybackOutput(
    private val context: Context,
    private val localPlayer: ExoPlayer,
    autoplay: () -> Boolean,
    private val onPresentationChanged: (PlaybackPresentation) -> Unit,
) {
    private val server = LocalCastServer(
        context,
        DefaultDataSource.Factory(context),
    )
    private val converter = CastMediaItemConverter(
        context,
        server,
    )
    private var transfer: CastTransferCallback? = null
    private var sessionManager: SessionManager? = null
    private var remotePlayer: Player? = null
    private var presentation = PlaybackPresentation()
    private var localBeforeConnection: PlayerTransferState? = null
    private var lastRemote: PlayerTransferState? = null
    private var restoreAfterDisconnect: PlayerTransferState? = null
    private var expectedMediaId: String? = null
    private var routeId: String? = null
    private var stopping = false
    private var returningToPhone = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { cancelConnection() }

    private fun publish(
        connection: PlaybackConnection,
        expand: Boolean = false,
        name: String? = null,
    ) {
        presentation = presentation.copy(
            connection = connection,
            deviceName = name ?: presentation.deviceName,
            expansionRequest = presentation.expansionRequest + if (expand) 1 else 0,
        )
        onPresentationChanged(presentation)
    }

    private fun beginConnection(session: CastSession?, explicit: Boolean) {
        if (stopping || localPlayer.mediaItemCount == 0) return
        if (presentation.connection != PlaybackConnection.CONNECTING) {
            localBeforeConnection = playbackTransferState(localPlayer).let { state ->
                if (!explicit && presentation.connection == PlaybackConnection.LOST) {
                    state.buildUpon().setPlayWhenReady(lastRemote?.playWhenReady ?: false).build()
                } else {
                    state
                }
            }
            expectedMediaId = localPlayer.currentMediaItem?.mediaId
        }
        routeId = MediaRouter.getInstance(context).selectedRoute.id
        localPlayer.pause()
        publish(
            PlaybackConnection.CONNECTING,
            explicit,
            session?.castDevice?.friendlyName,
        )
        handler.removeCallbacks(timeout)
        handler.postDelayed(
            timeout,
            20_000,
        )
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = beginConnection(
            session,
            true,
        )
        override fun onSessionStarted(session: CastSession, sessionId: String) = acknowledgeRemote()
        override fun onSessionStartFailed(session: CastSession, error: Int) = cancelConnection()
        override fun onSessionResuming(session: CastSession, sessionId: String) = beginConnection(
            session,
            false,
        )
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) =
            acknowledgeRemote()
        override fun onSessionResumeFailed(session: CastSession, error: Int) = connectionLost()
        override fun onSessionSuspended(session: CastSession, reason: Int) = connectionLost()
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (presentation.connection == PlaybackConnection.CONNECTING) {
                cancelConnection()
            } else if (!stopping && !returningToPhone) {
                connectionLost()
            }
        }
    }

    private fun connectionLost() {
        handler.removeCallbacks(timeout)
        if (stopping || returningToPhone) return
        localPlayer.pause()
        publish(PlaybackConnection.LOST)
    }

    private fun acknowledgeRemote() {
        val remote = remotePlayer ?: return
        if (stopping || presentation.connection == PlaybackConnection.LOCAL) return
        if (!isAcknowledgedCastMedia(
                remote,
                expectedMediaId,
            )
        ) {
            return
        }
        lastRemote = transfer?.snapshotForPhone(remote)
        handler.removeCallbacks(timeout)
        publish(PlaybackConnection.CAST)
    }

    val player: Player = try {
        val remote = RemoteCastPlayer.Builder(context).setMediaItemConverter(converter).build()
        remotePlayer = remote
        val callback = CastTransferCallback(
            remote,
            autoplay,
        ).also { transfer = it }
        CastPlayer.Builder(context)
            .setLocalPlayer(localPlayer)
            .setRemotePlayer(remote)
            .setTransferCallback { source, target ->
                try {
                    if (target === remote) {
                        if (stopping) {
                            target.pause()
                            target.clearMediaItems()
                        } else {
                            if (presentation.connection != PlaybackConnection.CONNECTING) {
                                beginConnection(
                                    sessionManager?.currentCastSession,
                                    false,
                                )
                            }
                            val intendedPlaying = localBeforeConnection?.playWhenReady ?: false
                            callback.transferState(
                                object : ForwardingPlayer(source) {
                                    override fun getPlayWhenReady() = intendedPlaying
                                },
                                target,
                            )
                        }
                    } else {
                        val restore = restoreAfterDisconnect
                        when {
                            restore != null -> {
                                restore.setToPlayer(target)
                                restoreAfterDisconnect = null
                                target.prepare()
                            }

                            stopping -> callback.transferState(
                                source,
                                target,
                            )

                            presentation.connection == PlaybackConnection.CONNECTING -> {
                                localBeforeConnection?.setToPlayer(target)
                                target.prepare()
                                publish(PlaybackConnection.LOCAL)
                            }

                            else -> {
                                if (source.mediaItemCount > 0) {
                                    callback.transferState(
                                        source,
                                        target,
                                    )
                                } else {
                                    lastRemote?.setToPlayer(target)
                                }
                                target.playWhenReady = returningToPhone &&
                                    (lastRemote?.playWhenReady ?: source.playWhenReady)
                                if (!returningToPhone) connectionLost()
                            }
                        }
                    }
                } catch (error: Exception) {
                    Logger.logError(
                        "Cast",
                        "Playback transfer failed: ${error.message}",
                    )
                    target.pause()
                    handler.post { cancelConnection() }
                }
            }
            .build()
            .also { output ->
                sessionManager = CastContext.getSharedInstance(context).sessionManager.also {
                    it.addSessionManagerListener(
                        sessionListener,
                        CastSession::class.java,
                    )
                }
                remote.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        acknowledgeRemote()
                    }
                })
                output.addListener(
                    CastResourceCleanup(output) {
                        server.stop()
                        converter.clear()
                    },
                )
            }
    } catch (error: RuntimeException) {
        Logger.logError(
            "Cast",
            "Cast unavailable: ${error.message}",
        )
        localPlayer
    }

    fun snapshotForPhone(): PlayerTransferState =
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            if (player.mediaItemCount > 0) {
                transfer?.snapshotForPhone(player)
                    ?: playbackTransferState(player)
            } else {
                lastRemote ?: localBeforeConnection ?: playbackTransferState(localPlayer)
            }
        } else {
            playbackTransferState(player)
        }

    fun cancelConnection() {
        if (presentation.connection != PlaybackConnection.CONNECTING) return
        handler.removeCallbacks(timeout)
        restoreAfterDisconnect = localBeforeConnection
        returningToPhone = true
        sessionManager?.endCurrentSession(true)
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
            restoreAfterDisconnect?.setToPlayer(localPlayer)
            restoreAfterDisconnect = null
            localPlayer.prepare()
        }
        publish(PlaybackConnection.LOCAL)
    }

    fun retry() {
        val route = MediaRouter.getInstance(context).routes.find {
            it.id == routeId && it.isEnabled
        }
        if (route == null) {
            publish(
                PlaybackConnection.LOST,
                expand = true,
            )
            return
        }
        stopping = false
        returningToPhone = false
        beginConnection(
            null,
            true,
        )
        route.select()
    }

    fun playOnPhone() {
        handler.removeCallbacks(timeout)
        returningToPhone = true
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            lastRemote = snapshotForPhone()
            sessionManager?.endCurrentSession(true)
        } else {
            localPlayer.prepare()
            localPlayer.play()
        }
        publish(PlaybackConnection.LOCAL)
    }

    fun restoreOnPhone(state: PlayerTransferState) {
        restoreAfterDisconnect = state
        returningToPhone = true
        stopping = false
        sessionManager?.endCurrentSession(true)
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
            state.setToPlayer(localPlayer)
            restoreAfterDisconnect = null
            localPlayer.prepare()
        }
        publish(PlaybackConnection.LOCAL)
    }

    fun onNewQueue() {
        transfer?.clearSavedQueue()
        lastRemote = null
        localBeforeConnection = null
        restoreAfterDisconnect = null
        expectedMediaId = null
    }

    fun onQueueReady(mediaId: String? = null) {
        stopping = false
        returningToPhone = false
        transfer?.onQueueReady()
        expectedMediaId = mediaId ?: player.currentMediaItem?.mediaId
    }

    fun onStop() {
        stopping = true
        returningToPhone = false
        restoreAfterDisconnect = null
        handler.removeCallbacks(timeout)
        transfer?.onStop()
        try {
            sessionManager?.endCurrentSession(true)
        } catch (error: RuntimeException) {
            Logger.logError(
                "Cast",
                "Cast disconnect failed: ${error.message}",
            )
        }
        publish(PlaybackConnection.LOCAL)
        server.stop()
        converter.clear()
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(
            sessionListener,
            CastSession::class.java,
        )
        onStop()
        player.release()
    }
}

@UnstableApi
internal fun isAcknowledgedCastMedia(player: Player, expectedMediaId: String?): Boolean =
    expectedMediaId != null && player.currentMediaItem?.mediaId == expectedMediaId &&
        player.currentMediaItem?.localConfiguration != null &&
        player.playbackState == Player.STATE_READY &&
        player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) &&
        player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)

@UnstableApi
internal class CastResourceCleanup(private val player: Player, private val clear: () -> Unit) :
    Player.Listener {
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        // A Cast queue stays empty until the receiver acknowledges the asynchronous load.
        if (timeline.isEmpty && player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
            clear()
        }
    }

    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
        if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) clear()
    }
}
