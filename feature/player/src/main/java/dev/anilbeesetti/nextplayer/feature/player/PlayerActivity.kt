package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.core.common.extensions.getInitialDirectoryUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getMediaContentUri
import dev.anilbeesetti.nextplayer.core.common.service.registerForSuspendActivityResult
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.extensions.OpenDocumentAtInitialUri
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import dev.anilbeesetti.nextplayer.feature.player.extensions.uriToSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.service.addAudioTrack
import dev.anilbeesetti.nextplayer.feature.player.service.addSubtitleTrack
import dev.anilbeesetti.nextplayer.feature.player.service.decoderServiceState
import dev.anilbeesetti.nextplayer.feature.player.service.setAudioDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.setVideoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.service.tryDecoderFallback
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaController
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.player.utils.PlaylistPlaybackContract
import dev.anilbeesetti.nextplayer.feature.player.utils.toMediaQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

internal fun shouldResumeExistingPlayback(
    returningFromBackground: Boolean,
    isRequestedUriCurrent: Boolean,
    hasExplicitPlaylist: Boolean,
): Boolean = returningFromBackground || (isRequestedUriCurrent && !hasExplicitPlaylist)

@SuppressLint("UnsafeOptInUsageError")
class PlayerActivity : ComponentActivity() {

    private val playlistRepository: PlaylistRepository by inject()

    private val viewModel: PlayerViewModel by viewModel { parametersOf(playerOutput()) }
    val playerPreferences get() = viewModel.state.value.playerPreferences

    private val onWindowAttributesChangedListener = CopyOnWriteArrayList<Consumer<WindowManager.LayoutParams?>>()

    private var isPlaybackFinished = false
    private var playInBackground: Boolean = false
    private var isIntentNew: Boolean = true

    private var mediaController by mutableStateOf<MediaController?>(null)
    private lateinit var playerApi: PlayerApi
    private var playbackRequestJob: Job? = null

    private val playbackStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            intent.data = mediaItem?.localConfiguration?.uri
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateKeepScreenOnFlag()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                isPlaybackFinished = mediaController?.playbackState == Player.STATE_ENDED
                finishAndStopPlayerSession()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                mediaController?.repeatMode == Player.REPEAT_MODE_OFF
            ) {
                isPlaybackFinished = true
                finishAndStopPlayerSession()
            }
        }
    }

    private val audioFileSuspendLauncher = registerForSuspendActivityResult(OpenDocumentAtInitialUri())

    private val subtitleFileSuspendLauncher = registerForSuspendActivityResult(OpenDocumentAtInitialUri())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        viewModel.output = playerOutput()
        playerApi = PlayerApi(this)
        setContent {
            val player = rememberMediaController(
                onBeforeRelease = ::onControllerStopped,
                onExtrasChanged = { extras ->
                    viewModel.onAction(PlayerAction.UpdateDecoderServiceState(extras.decoderServiceState()))
                },
            )
            LaunchedEffect(player) {
                if (player == null || !player.isConnected) return@LaunchedEffect
                mediaController = player
                viewModel.onAction(PlayerAction.UpdateDecoderServiceState(player.sessionExtras.decoderServiceState()))
                player.addListener(playbackStateListener)
                updateKeepScreenOnFlag()
                startPlayback()
            }

            NextPlayerTheme(darkTheme = true) {
                MediaPlayerScreen(
                    viewModel = viewModel,
                    player = player,
                )
            }
        }
    }

    private fun playerOutput() = PlayerViewModel.Output(
        navigateUp = ::finishAndStopPlayerSession,
        selectSubtitle = ::selectSubtitle,
        selectAudio = ::selectAudio,
        playInBackground = {
            playInBackground = true
            finish()
        },
        setVideoDecoderMode = { mode ->
            lifecycleScope.launch { mediaController?.setVideoDecoderMode(mode) }
        },
        setAudioDecoderMode = { mode ->
            lifecycleScope.launch { mediaController?.setAudioDecoderMode(mode) }
        },
        tryDecoderFallback = {
            lifecycleScope.launch { mediaController?.tryDecoderFallback() }
        },
    )

    private suspend fun currentMediaDirectory(): Uri? {
        val uri = mediaController?.currentMediaItem?.localConfiguration?.uri ?: return null
        return withContext(Dispatchers.IO) { getInitialDirectoryUri(uri) }
    }

    private fun selectSubtitle() {
        lifecycleScope.launch {
            val uri = subtitleFileSuspendLauncher.launch(
                OpenDocumentAtInitialUri.Input(
                    mimeTypes = arrayOf(
                        MimeTypes.APPLICATION_SUBRIP,
                        MimeTypes.APPLICATION_TTML,
                        MimeTypes.TEXT_VTT,
                        MimeTypes.TEXT_SSA,
                        MimeTypes.BASE_TYPE_APPLICATION + "/octet-stream",
                        MimeTypes.BASE_TYPE_TEXT + "/*",
                    ),
                    initialUri = currentMediaDirectory(),
                ),
            ) ?: return@launch
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            snapshotFlow { mediaController }.filterNotNull().first().addSubtitleTrack(uri)
        }
    }

    private fun selectAudio() {
        lifecycleScope.launch {
            val uri = audioFileSuspendLauncher.launch(
                OpenDocumentAtInitialUri.Input(
                    mimeTypes = arrayOf("audio/*", "application/ogg"),
                    initialUri = currentMediaDirectory(),
                ),
            ) ?: return@launch
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val controller = snapshotFlow { mediaController }.filterNotNull().first()
                if (!controller.addAudioTrack(uri)) {
                    Toast.makeText(this@PlayerActivity, coreUiR.string.error_opening_audio, Toast.LENGTH_LONG).show()
                }
            } catch (_: SecurityException) {
                Toast.makeText(this@PlayerActivity, coreUiR.string.error_opening_audio, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onControllerStopped() {
        playbackRequestJob?.cancel()
        mediaController?.run {
            viewModel.onAction(PlayerAction.UpdatePlayWhenReady(playWhenReady))
            removeListener(playbackStateListener)
            val shouldPlayInBackground = playInBackground || playerPreferences.autoBackgroundPlay
            if (subtitleFileSuspendLauncher.isAwaitingResult || audioFileSuspendLauncher.isAwaitingResult || !shouldPlayInBackground) {
                pause()
            }
            if (isInPictureInPictureMode) {
                finish()
                if (!shouldPlayInBackground) stopPlayerSession()
            }
        }
        mediaController = null
        updateKeepScreenOnFlag()
    }

    private fun startPlayback() {
        val uri = intent.data ?: return
        val controller = mediaController ?: return

        val currentUri = controller.currentMediaItem?.localConfiguration?.uri
        val hasExplicitPlaylist = intent.hasExtra(PlayerApi.API_PLAYLIST) ||
            intent.hasExtra(PlaylistPlaybackContract.EXTRA_PLAYLIST_ID)

        if (shouldResumeExistingPlayback(
                returningFromBackground = !isIntentNew && controller.currentMediaItem != null,
                isRequestedUriCurrent = currentUri.toString() == uri.toString(),
                hasExplicitPlaylist = hasExplicitPlaylist,
            )
        ) {
            controller.prepare()
            controller.playWhenReady = viewModel.state.value.playWhenReady
            return
        }

        isIntentNew = false

        playbackRequestJob?.cancel()
        playbackRequestJob = lifecycleScope.launch {
            playVideo(
                uri = uri,
                playlistId = intent.playlistIdOrNull(),
            )
        }
    }

    private suspend fun playVideo(
        uri: Uri,
        playlistId: Long?,
    ) = withContext(Dispatchers.Default) {
        val savedQueue = playlistId
            ?.let { playlistRepository.getPlaylist(it) }
            ?.toMediaQueue(selectedUri = uri.toString())

        if (playlistId != null) {
            val mediaItems = savedQueue?.mediaItems ?: listOf(
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(uri.toString())
                    .build(),
            )
            val startIndex = savedQueue?.startIndex ?: 0
            ensureActive()
            withContext(Dispatchers.Main) {
                mediaController?.run {
                    setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
                    playWhenReady = viewModel.state.value.playWhenReady
                    prepare()
                }
            }
            return@withContext
        }

        val mediaContentUri = getMediaContentUri(uri)
        val playlist = playerApi.getPlaylist().takeIf { it.isNotEmpty() }
            ?: mediaContentUri?.let { mediaUri ->
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
                            subtitleEncoding = playerPreferences.subtitleTextEncoding,
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
                playWhenReady = viewModel.state.value.playWhenReady
                prepare()
            }
        }
    }

    private fun Intent.playlistIdOrNull(): Long? = getLongExtra(
        PlaylistPlaybackContract.EXTRA_PLAYLIST_ID,
        Long.MIN_VALUE,
    ).takeUnless { it == Long.MIN_VALUE }

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
            startPlayback()
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
