package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.common.extensions.getMediaContentUri
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.extensions.registerForSuspendActivityResult
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import dev.anilbeesetti.nextplayer.feature.player.extensions.uriToSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.addSubtitleTrack
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalHidePlayerButtonsBackground = compositionLocalOf { false }

@SuppressLint("UnsafeOptInUsageError")
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    val playerPreferences get() = viewModel.uiState.value.playerPreferences

    private val onWindowAttributesChangedListener = CopyOnWriteArrayList<Consumer<WindowManager.LayoutParams?>>()

    private var isPlaybackFinished = false
    private var playInBackground: Boolean = false
    private var isIntentNew: Boolean = true

    /**
     * Player
     */
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var playerApi: PlayerApi

    /**
     * Listeners
     */
    private val playbackStateListener: Player.Listener = playbackStateListener()

    private val subtitleFileSuspendLauncher = registerForSuspendActivityResult(OpenDocument())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var player by remember { mutableStateOf<MediaController?>(null) }

            LifecycleStartEffect(Unit) {
                maybeInitControllerFuture()
                lifecycleScope.launch {
                    player = controllerFuture?.await()
                }

                onStopOrDispose {
                    player = null
                }
            }

            CompositionLocalProvider(LocalHidePlayerButtonsBackground provides (uiState.playerPreferences?.hidePlayerButtonsBackground == true)) {
                NextPlayerTheme(darkTheme = true) {
                    MediaPlayerScreen(
                        player = player ?: return@NextPlayerTheme,
                        viewModel = viewModel,
                        playerPreferences = uiState.playerPreferences ?: return@NextPlayerTheme,
                        onSelectSubtitleClick = {
                            lifecycleScope.launch {
                                val uri = subtitleFileSuspendLauncher.launch(
                                    arrayOf(
                                        MimeTypes.APPLICATION_SUBRIP,
                                        MimeTypes.APPLICATION_TTML,
                                        MimeTypes.TEXT_VTT,
                                        MimeTypes.TEXT_SSA,
                                        MimeTypes.BASE_TYPE_APPLICATION + "/octet-stream",
                                        MimeTypes.BASE_TYPE_TEXT + "/*",
                                    ),
                                ) ?: return@launch
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                maybeInitControllerFuture()
                                controllerFuture?.await()?.addSubtitleTrack(uri)
                            }
                        },
                        onBackClick = { finishAndStopPlayerSession() },
                        onPlayInBackgroundClick = {
                            playInBackground = true
                            finish()
                        },
                    )
                }
            }
        }

        playerApi = PlayerApi(this)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            maybeInitControllerFuture()
            mediaController = controllerFuture?.await()

            mediaController?.run {
                updateKeepScreenOnFlag()
                addListener(playbackStateListener)
                startPlayback()
            }
        }
    }

    override fun onStop() {
        mediaController?.run {
            viewModel.playWhenReady = playWhenReady
            removeListener(playbackStateListener)
        }
        val shouldPlayInBackground = playInBackground || playerPreferences?.autoBackgroundPlay == true
        if (subtitleFileSuspendLauncher.isAwaitingResult || !shouldPlayInBackground) {
            mediaController?.pause()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            finish()
            if (!shouldPlayInBackground) {
                mediaController?.stopPlayerSession()
            }
        }

        controllerFuture?.run {
            MediaController.releaseFuture(this)
            controllerFuture = null
        }
        super.onStop()
    }

    private fun maybeInitControllerFuture() {
        if (controllerFuture == null) {
            val sessionToken = SessionToken(applicationContext, ComponentName(applicationContext, PlayerService::class.java))
            controllerFuture = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        }
    }

    private fun startPlayback() {
        val uri = intent.data ?: return

        val returningFromBackground = !isIntentNew && mediaController?.currentMediaItem != null
        val isNewUriTheCurrentMediaItem = mediaController?.currentMediaItem?.localConfiguration?.uri.toString() == uri.toString()

        if (returningFromBackground || isNewUriTheCurrentMediaItem) {
            mediaController?.prepare()
            mediaController?.playWhenReady = viewModel.playWhenReady
            return
        }

        isIntentNew = false

        lifecycleScope.launch {
            playVideo(uri)
        }
    }

    private suspend fun playVideo(uri: Uri) = withContext(Dispatchers.Default) {
        val mediaContentUri = getMediaContentUri(uri)
        val playlist = mediaContentUri?.let { mediaUri ->
            viewModel.getPlaylistFromUri(mediaUri)
                .map { it.uriString }
                .toMutableList()
                .apply {
                    if (!contains(mediaUri.toString())) {
                        add(index = 0, element = mediaUri.toString())
                    }
                }
        } ?: listOf(uri.toString())

        val mediaItemIndexToPlay = playlist.indexOfFirst {
            it == (mediaContentUri ?: uri).toString()
        }.takeIf { it >= 0 } ?: 0

        val mediaItems = playlist.mapIndexed { index, uri ->
            MediaItem.Builder().apply {
                setUri(uri)
                setMediaId(uri)
                if (index == mediaItemIndexToPlay) {
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(playerApi.title)
                            setExtras(positionMs = playerApi.position?.toLong())
                        }.build(),
                    )
                    val apiSubs = playerApi.getSubs().map { subtitle ->
                        uriToSubtitleConfiguration(
                            uri = subtitle.uri,
                            subtitleEncoding = playerPreferences?.subtitleTextEncoding ?: "",
                            isSelected = subtitle.isSelected,
                        )
                    }
                    setSubtitleConfigurations(apiSubs)
                }
            }.build()
        }

        withContext(Dispatchers.Main) {
            mediaController?.run {
                setMediaItems(mediaItems, mediaItemIndexToPlay, playerApi.position?.toLong() ?: C.TIME_UNSET)
                playWhenReady = viewModel.playWhenReady
                prepare()
            }
        }
    }

    private fun playbackStateListener() = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            intent.data = mediaItem?.localConfiguration?.uri
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            updateKeepScreenOnFlag()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_ENDED -> {
                    isPlaybackFinished = mediaController?.playbackState == Player.STATE_ENDED
                    finishAndStopPlayerSession()
                }
                else -> {}
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                if (mediaController?.repeatMode != Player.REPEAT_MODE_OFF) return
                isPlaybackFinished = true
                finishAndStopPlayerSession()
            }
        }
    }

    override fun finish() {
        if (playerApi.shouldReturnResult) {
            val result = playerApi.getResult(
                isPlaybackFinished = isPlaybackFinished,
                duration = mediaController?.duration ?: C.TIME_UNSET,
                position = mediaController?.currentPosition ?: C.TIME_UNSET,
            )
            setResult(RESULT_OK, result)
        }
        super.finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            setIntent(intent)
            isIntentNew = true
            if (mediaController != null) {
                startPlayback()
            }
        }
    }

    private fun updateKeepScreenOnFlag() {
        if (mediaController?.isPlaying == true) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun finishAndStopPlayerSession() {
        finish()
        mediaController?.stopPlayerSession()
    }

    override fun onWindowAttributesChanged(params: WindowManager.LayoutParams?) {
        super.onWindowAttributesChanged(params)
        for (listener in onWindowAttributesChangedListener) {
            listener.accept(params)
        }
    }

    fun addOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.add(listener)
    }

    fun removeOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.remove(listener)
    }
}
