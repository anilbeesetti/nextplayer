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
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberMediaController(
    onBeforeRelease: () -> Unit = {},
    onExtrasChanged: (Bundle) -> Unit = {},
): MediaController? {
    val context = LocalContext.current.applicationContext
    val token = remember(context) { SessionToken(context, ComponentName(context, PlayerService::class.java)) }
    val scope = rememberCoroutineScope()
    var mediaController by remember(context) { mutableStateOf<MediaController?>(null) }
    val currentOnBeforeRelease by rememberUpdatedState(onBeforeRelease)
    val currentOnExtrasChanged by rememberUpdatedState(onExtrasChanged)

    LifecycleStartEffect(context) {
        val future = MediaController.Builder(context, token).setListener(
            object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    if (controller === mediaController) currentOnExtrasChanged(extras)
                }
            },
        ).buildAsync()
        val connection = scope.launch { mediaController = future.await() }

        onStopOrDispose {
            connection.cancel()
            try {
                currentOnBeforeRelease()
            } finally {
                mediaController = null
                MediaController.releaseFuture(future)
            }
        }
    }
    return mediaController
}
