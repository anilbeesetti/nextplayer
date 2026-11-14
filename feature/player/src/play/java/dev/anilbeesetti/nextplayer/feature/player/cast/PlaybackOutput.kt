package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaSeekOptions
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
    private var restoredOnPhone = false
    private var expectedMediaId: String? = null
    private var outboundPlaying = false
    private var routeId: String? = null
    private var stopping = false
    private var returningToPhone = false
    private var rejectRemoteTransfers = false
    private var receiverAhead = false
    private var sentPositionMs: Long? = null
    private var placement = Placement.LOADING

    private enum class Placement { LOADING, STARTING, PLACING }
    private val handler = Handler(Looper.getMainLooper())

    // The receiver reports what it plays at once; Media3's queue positions can lag it by seconds.
    private var receiverMediaId: () -> String? = {
        sessionManager?.currentCastSession?.remoteMediaClient?.mediaInfo?.contentId
    }
    private val receiverDurationMs: () -> Long = {
        sessionManager?.currentCastSession?.remoteMediaClient?.mediaInfo?.streamDuration
            ?: C.TIME_UNSET
    }
    private var syncedPlayer: ReceiverSyncedPlayer? = null
    private val timeout = Runnable { cancelConnection() }
    private val snapshotTick = object : Runnable {
        override fun run() {
            acknowledgeRemote()
            handler.postDelayed(
                this,
                500,
            )
        }
    }
    val hasPendingPlayback: Boolean get() =
        presentation.connection == PlaybackConnection.CONNECTING && localBeforeConnection != null

    private fun publish(
        connection: PlaybackConnection,
        expand: Boolean = false,
        name: String? = null,
    ) {
        val retained = if (connection ==
            PlaybackConnection.CONNECTING
        ) {
            localBeforeConnection
        } else {
            lastRemote
        }
        presentation = presentation.copy(
            mediaItem = retained?.mediaItems?.getOrNull(retained.currentMediaItemIndex),
            position = retained?.currentPosition ?: 0,
            connection = connection,
            deviceName = name ?: presentation.deviceName,
            expansionRequest = presentation.expansionRequest + if (expand) 1 else 0,
        )
        val synced = lastRemote.takeIf { connection == PlaybackConnection.CAST && receiverAhead }
        syncedPlayer?.showReceiver(
            synced?.mediaItems.orEmpty(),
            synced?.currentMediaItemIndex ?: C.INDEX_UNSET,
            receiverDurationMs(),
        )
        onPresentationChanged(presentation)
    }

    private fun beginConnection(session: CastSession?, explicit: Boolean) {
        if ((!explicit && rejectRemoteTransfers) || stopping ||
            localPlayer.mediaItemCount == 0
        ) {
            return
        }
        if (explicit) {
            rejectRemoteTransfers = false
            restoreAfterDisconnect = null
        }
        if (presentation.connection != PlaybackConnection.CONNECTING) {
            localBeforeConnection = playbackTransferState(localPlayer)
            // Reconnecting after a loss resumes what the receiver was doing, not the paused phone.
            outboundPlaying = if (presentation.connection == PlaybackConnection.LOST) {
                lastRemote?.playWhenReady ?: false
            } else {
                localBeforeConnection?.playWhenReady ?: false
            }
            expectedMediaId = localPlayer.currentMediaItem?.mediaId
            sentPositionMs = null
        }
        val selected = MediaRouter.getInstance(context).selectedRoute
        routeId = selected.id
        (session?.castDevice ?: CastDevice.getFromBundle(selected.extras))?.deviceId
            ?.let { castDeviceId = it }
        returningToPhone = false
        receiverAhead = false
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
        if (stopping || returningToPhone || rejectRemoteTransfers) return
        if (presentation.connection != PlaybackConnection.CONNECTING &&
            presentation.connection != PlaybackConnection.CAST
        ) {
            return
        }
        val playing = receiverMediaId() ?: remote.currentMediaItem
            ?.takeIf { it.localConfiguration != null }?.mediaId
        val mediaId = if (presentation.connection == PlaybackConnection.CAST) {
            playing
        } else {
            expectedMediaId
        }
        if (!isAcknowledgedCastMedia(
                remote,
                mediaId,
                playing,
            )
        ) {
            return
        }
        // This receiver can start a load anywhere but its requested position and applies that
        // position when playback first starts, which undoes or hangs a seek sent earlier. So the
        // item loads paused at 0; it then starts muted, and only a playing item is placed.
        val sent = sentPositionMs
        if (presentation.connection == PlaybackConnection.CONNECTING && sent != null) {
            val client = sessionManager?.currentCastSession?.remoteMediaClient
            if (client == null) {
                sentPositionMs = null
                remote.seekTo(sent)
                remote.playWhenReady = outboundPlaying
                return
            }
            when (placement) {
                Placement.LOADING -> if (client.isPaused) {
                    client.setStreamMute(true)
                    client.play()
                    placement = Placement.STARTING
                }

                Placement.STARTING -> if (client.isPlaying) {
                    client.seek(
                        MediaSeekOptions.Builder().setPosition(sent).setResumeState(
                            if (outboundPlaying) {
                                MediaSeekOptions.RESUME_STATE_PLAY
                            } else {
                                MediaSeekOptions.RESUME_STATE_PAUSE
                            },
                        ).build(),
                    )
                    placement = Placement.PLACING
                }

                Placement.PLACING -> if ((client.isPlaying || client.isPaused) &&
                    client.approximateStreamPosition >= sent - PLACED_TOLERANCE_MS
                ) {
                    client.setStreamMute(false)
                    sentPositionMs = null
                }
            }
            if (sentPositionMs != null) return
        }
        val behind = remote.currentMediaItem?.mediaId != playing
        val wasAhead = receiverAhead
        // Only right after connecting: later, Media3 runs ahead of the receiver on a skip.
        receiverAhead = behind && (
            presentation.connection == PlaybackConnection.CONNECTING || receiverAhead
            )
        lastRemote = receiverState(
            remote,
            playing.takeIf { receiverAhead },
        )
        handler.removeCallbacks(timeout)
        val shown = lastRemote?.let { it.mediaItems.getOrNull(it.currentMediaItemIndex)?.mediaId }
        if (presentation.connection != PlaybackConnection.CAST ||
            presentation.mediaItem?.mediaId != shown ||
            wasAhead != receiverAhead
        ) {
            publish(PlaybackConnection.CAST)
        }
    }

    /** Places the queue on the item the receiver plays when Media3's queue has not caught up. */
    private fun receiverState(remote: Player, mediaId: String?): PlayerTransferState? {
        val state = transfer?.snapshotForPhone(remote) ?: return null
        val current = state.mediaItems.getOrNull(state.currentMediaItemIndex)
        if (mediaId == null || current?.mediaId == mediaId) return state
        val queue = state.mediaItems.takeIf { items -> items.any { it.mediaId == mediaId } }
            ?: localBeforeConnection?.mediaItems
            ?: return state
        val index = queue.indexOfFirst { it.mediaId == mediaId }
        if (index < 0) return state
        return state.buildUpon().setMediaItems(queue).setCurrentMediaItemIndex(index).build()
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
                transferState(
                    source,
                    target,
                    remote,
                    callback,
                )
            }
            .build()
            .also { output ->
                sessionManager = CastContext.getSharedInstance(context).sessionManager.also {
                    it.addSessionManagerListener(
                        sessionListener,
                        CastSession::class.java,
                    )
                }
                handler.post(snapshotTick)
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
            .let { output -> ReceiverSyncedPlayer(output).also { syncedPlayer = it } }
    } catch (error: RuntimeException) {
        Logger.logError(
            "Cast",
            "Cast unavailable: ${error.message}",
        )
        localPlayer
    }

    internal fun transferState(
        source: Player,
        target: Player,
        remote: Player,
        callback: CastTransferCallback,
    ) {
        try {
            if (target === remote) {
                if (stopping || rejectRemoteTransfers) {
                    target.pause()
                    target.clearMediaItems()
                    handler.post { sessionManager?.endCurrentSession(true) }
                } else {
                    if (presentation.connection != PlaybackConnection.CONNECTING) {
                        beginConnection(
                            sessionManager?.currentCastSession,
                            false,
                        )
                    }
                    sentPositionMs = source.currentPosition
                    placement = Placement.LOADING
                    callback.transferState(
                        object : ForwardingPlayer(source) {
                            override fun getPlayWhenReady() = false
                            override fun getCurrentPosition() = 0L
                            override fun getContentPosition() = 0L
                        },
                        target,
                    )
                }
            } else {
                val restore = restoreAfterDisconnect
                when {
                    restore != null -> {
                        if (!restoredOnPhone) {
                            restore.setToPlayer(target)
                            target.prepare()
                            restoredOnPhone = true
                        }
                        if (cancelWaiting) {
                            handler.removeCallbacks(finishCancel)
                            cancelWaiting = false
                            publish(PlaybackConnection.LOCAL)
                        }
                    }

                    stopping -> {
                        target.pause()
                        target.clearMediaItems()
                        target.stop()
                    }

                    presentation.connection == PlaybackConnection.CONNECTING -> {
                        localBeforeConnection?.setToPlayer(target)
                        target.prepare()
                        publish(PlaybackConnection.LOCAL)
                    }

                    else -> {
                        if (source.currentMediaItem?.localConfiguration != null) {
                            callback.transferState(
                                source,
                                target,
                            )
                        } else {
                            lastRemote?.setToPlayer(target)
                        }
                        target.playWhenReady = returningToPhone &&
                            (lastRemote?.playWhenReady ?: source.playWhenReady)
                        // Media3 prepares only after a busy receiver; one that ended on its own is
                        // idle, which would leave the phone without the item's duration or frame.
                        target.prepare()
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

    fun snapshotForPhone(): PlayerTransferState =
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            if (player.currentMediaItem?.localConfiguration != null) {
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
        stopRetryScan()
        handler.removeCallbacks(timeout)
        restoreAfterDisconnect = localBeforeConnection
        restoredOnPhone = false
        rejectRemoteTransfers = true
        returningToPhone = true
        // Ending a session that is still launching leaves its receiver app open on the TV. Let the
        // launch finish: the rejected transfer then ends the session and restores the phone.
        if (!cancelWaiting && sessionManager?.currentCastSession?.isConnecting == true) {
            cancelWaiting = true
            handler.postDelayed(
                finishCancel,
                CANCEL_LAUNCH_WAIT_MS,
            )
            return
        }
        finishCancel.run()
    }

    private var cancelWaiting = false
    private val finishCancel = Runnable {
        cancelWaiting = false
        handler.removeCallbacks(timeout)
        publish(PlaybackConnection.LOCAL)
        sessionManager?.endCurrentSession(true)
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL && !restoredOnPhone) {
            restoreAfterDisconnect?.setToPlayer(localPlayer)
            localPlayer.prepare()
            restoredOnPhone = true
        }
    }

    fun retry() {
        val router = MediaRouter.getInstance(context)
        val id = routeId
        val route = router.routes.find { it.isRetryTarget() }
        if (route != null) {
            reconnect(route)
            return
        }
        // Nothing scans while the connection is lost, so the receiver can be missing until found.
        val selector = try {
            CastContext.getSharedInstance(context).mergedSelector
        } catch (_: RuntimeException) {
            null
        }
        if ((id == null && castDeviceId == null) || selector == null) {
            publish(
                PlaybackConnection.LOST,
                expand = true,
            )
            return
        }
        stopRetryScan()
        beginConnection(
            null,
            true,
        )
        val scan = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) =
                found(route)

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) =
                found(route)

            private fun found(route: MediaRouter.RouteInfo) {
                if (!route.isRetryTarget()) return
                stopRetryScan()
                reconnect(route)
            }
        }
        retryScan = scan
        router.addCallback(
            selector,
            scan,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
        )
        handler.postDelayed(
            retryGiveUp,
            RETRY_SCAN_MS,
        )
    }

    private var retryScan: MediaRouter.Callback? = null

    // A connected session selects a group route around the receiver; retry needs the receiver.
    private var castDeviceId: String? = null

    private fun MediaRouter.RouteInfo.isRetryTarget(): Boolean {
        if (!isEnabled || isDefault) return false
        val deviceId = CastDevice.getFromBundle(extras)?.deviceId
        return if (deviceId != null &&
            castDeviceId != null
        ) {
            deviceId == castDeviceId
        } else {
            id == routeId
        }
    }
    private val retryGiveUp = Runnable {
        stopRetryScan()
        handler.removeCallbacks(timeout)
        publish(
            PlaybackConnection.LOST,
            expand = true,
        )
    }

    private fun stopRetryScan() {
        handler.removeCallbacks(retryGiveUp)
        retryScan?.let { MediaRouter.getInstance(context).removeCallback(it) }
        retryScan = null
    }

    private fun reconnect(route: MediaRouter.RouteInfo) {
        stopping = false
        returningToPhone = false
        val router = MediaRouter.getInstance(context)
        if (router.selectedRoute.id == route.id) {
            router.unselect(MediaRouter.UNSELECT_REASON_DISCONNECTED)
        }
        handler.post {
            beginConnection(
                null,
                true,
            )
            routeId = route.id
            route.select()
        }
    }

    fun playOnPhone() {
        stopRetryScan()
        rejectRemoteTransfers = true
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
        rejectRemoteTransfers = true
        restoreAfterDisconnect = state
        restoredOnPhone = false
        returningToPhone = true
        stopping = false
        sessionManager?.endCurrentSession(true)
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
            state.setToPlayer(localPlayer)
            localPlayer.prepare()
            restoredOnPhone = true
        }
        publish(PlaybackConnection.LOCAL)
    }

    fun showCastArtwork(mediaId: String, uri: Uri) {
        syncedPlayer?.showArtwork(
            mediaId,
            uri,
        )
    }

    fun onNewQueue() {
        transfer?.clearSavedQueue()
        lastRemote = null
        localBeforeConnection = null
        restoreAfterDisconnect = null
        expectedMediaId = null
    }

    fun onQueueReady(mediaId: String? = null, pendingState: PlayerTransferState? = null) {
        stopping = false
        returningToPhone = false
        transfer?.onQueueReady()
        expectedMediaId = mediaId ?: player.currentMediaItem?.mediaId
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            localBeforeConnection = pendingState ?: localBeforeConnection
            pendingState?.let { transfer?.retainFolderQueue(it.mediaItems) }
            publish(
                PlaybackConnection.CONNECTING,
                expand = true,
            )
            handler.removeCallbacks(timeout)
            handler.postDelayed(
                timeout,
                20_000,
            )
        } else {
            publish(PlaybackConnection.LOCAL)
        }
    }

    fun onStop() {
        stopRetryScan()
        rejectRemoteTransfers = true
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
        publish(PlaybackConnection.STOPPED)
        server.stop()
        converter.clear()
    }

    fun release() {
        handler.removeCallbacks(snapshotTick)
        sessionManager?.removeSessionManagerListener(
            sessionListener,
            CastSession::class.java,
        )
        onStop()
        player.release()
    }
}

private const val RETRY_SCAN_MS = 10_000L
private const val PLACED_TOLERANCE_MS = 1_500L
private const val CANCEL_LAUNCH_WAIT_MS = 10_000L

@UnstableApi
internal fun isAcknowledgedCastMedia(
    player: Player,
    expectedMediaId: String?,
    receiverMediaId: String? = player.currentMediaItem
        ?.takeIf { it.localConfiguration != null }?.mediaId,
): Boolean = expectedMediaId != null && receiverMediaId == expectedMediaId &&
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
