package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.ComponentName
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberMediaController(
    onBeforeRelease: () -> Unit = {},
    onExtrasChanged: (Bundle) -> Unit = {},
    ownsVideoSurface: Boolean = true,
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

    LifecycleStartEffect(
        context,
        ownsVideoSurface,
    ) {
        val future = MediaController.Builder(
            context,
            token,
        ).setListener(
            object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (controller === mediaController) mediaController = null
                }

                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    if (controller === mediaController) currentOnExtrasChanged(extras)
                }
            },
        ).buildAsync()
        val connection = scope.launch {
            try {
                mediaController = future.await()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mediaController = null
            }
        }

        onStopOrDispose {
            connection.cancel()
            try {
                if (ownsVideoSurface) {
                    mediaController?.clearVideoSurface()
                    // The service outlives these controls; clear suppression while connected.
                    mediaController?.setIsScrubbingModeEnabled(enabled = false)
                }
                currentOnBeforeRelease()
            } finally {
                mediaController = null
                MediaController.releaseFuture(future)
            }
        }
    }
    return mediaController
}
