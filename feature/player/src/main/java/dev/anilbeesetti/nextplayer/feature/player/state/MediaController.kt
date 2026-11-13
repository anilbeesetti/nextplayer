package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.ComponentName
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberMediaController(
    onBeforeRelease: () -> Unit = {},
    onExtrasChanged: (Bundle) -> Unit = {},
    ownsVideoSurface: Boolean = true,
    keepConnectedInBackground: Boolean = false,
): MediaController? {
    val context = LocalContext.current.applicationContext
    val token = remember(context) {
        SessionToken(
            context,
            ComponentName(
                context,
                PlayerService::class.java,
            ),
        )
    }
    val scope = rememberCoroutineScope()
    var mediaController by remember(context) { mutableStateOf<MediaController?>(value = null) }
    val currentOnBeforeRelease by rememberUpdatedState(newValue = onBeforeRelease)
    val currentOnExtrasChanged by rememberUpdatedState(newValue = onExtrasChanged)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(
        context,
        ownsVideoSurface,
        keepConnectedInBackground,
        lifecycle,
    ) {
        var future: ListenableFuture<MediaController>? = null
        var connection: Job? = null
        var connected: MediaController? = null
        lateinit var playerListener: Player.Listener

        fun release() {
            val previousFuture = future ?: return
            future = null
            connection?.cancel()
            connection = null
            val previous = connected
            connected = null
            previous?.removeListener(playerListener)
            try {
                if (ownsVideoSurface) {
                    previous?.clearVideoSurface()
                    previous?.setIsScrubbingModeEnabled(enabled = false)
                }
                currentOnBeforeRelease()
            } finally {
                if (mediaController === previous) mediaController = null
                MediaController.releaseFuture(previousFuture)
            }
        }

        fun releaseIfStopped() {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
            val current = connected
            if (keepConnectedInBackground && current != null) {
                val extras = current.sessionExtras
                val output = PlaybackPresentation.from(extras).connection
                val activeQueue = current.mediaItemCount > 0 &&
                    current.playbackState != Player.STATE_ENDED
                if (activeQueue || output in setOf(
                        PlaybackConnection.CONNECTING,
                        PlaybackConnection.CAST,
                        PlaybackConnection.LOST,
                    ) || extras.getBoolean(CustomCommands.SESSION_RELEASE_BLOCKED_KEY)
                ) {
                    return
                }
            }
            release()
        }

        // A stopped composition cannot run UI effects to release an idle session.
        playerListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = releaseIfStopped()
        }

        fun connect() {
            if (future != null) return
            val request = MediaController.Builder(
                context,
                token,
            ).setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        if (controller === connected) release()
                    }

                    override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                        if (controller === connected) {
                            currentOnExtrasChanged(extras)
                            releaseIfStopped()
                        }
                    }
                },
            ).buildAsync()
            future = request
            connection = scope.launch {
                try {
                    val result = request.await()
                    if (future !== request) return@launch
                    connected = result
                    result.addListener(playerListener)
                    mediaController = result
                    releaseIfStopped()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (future === request) release()
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> connect()
                Lifecycle.Event.ON_STOP -> releaseIfStopped()
                Lifecycle.Event.ON_DESTROY -> release()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) connect()
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }
    return mediaController
}
