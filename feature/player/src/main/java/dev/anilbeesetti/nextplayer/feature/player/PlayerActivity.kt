package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.common.extensions.getInitialDirectoryUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getMediaContentUri
import dev.anilbeesetti.nextplayer.core.common.service.registerForSuspendActivityResult
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.extensions.OpenDocumentAtInitialUri
import dev.anilbeesetti.nextplayer.feature.player.extensions.uriToSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.addAudioTrack
import dev.anilbeesetti.nextplayer.feature.player.service.addSubtitleTrack
import dev.anilbeesetti.nextplayer.feature.player.service.decoderServiceState
import dev.anilbeesetti.nextplayer.feature.player.service.retainPlaybackUriPermissions
import dev.anilbeesetti.nextplayer.feature.player.service.setAudioDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.setVideoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.startBackgroundPlayback
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.service.tryDecoderFallback
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaController
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.player.utils.PlaylistPlaybackContract
import dev.anilbeesetti.nextplayer.feature.player.utils.toMediaQueue
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

internal fun shouldResumeExistingPlayback(
    returningFromBackground: Boolean,
    isRequestedUriCurrent: Boolean,
    hasExplicitPlaylist: Boolean,
    hasNextMediaItem: Boolean,
): Boolean = returningFromBackground ||
    (isRequestedUriCurrent && !hasExplicitPlaylist && hasNextMediaItem)

@SuppressLint("UnsafeOptInUsageError")
class PlayerActivity : ComponentActivity() {

    private val playlistRepository: PlaylistRepository by inject()

    private val viewModel: PlayerViewModel by viewModel { parametersOf(playerOutput()) }
    val playerPreferences get() = viewModel.state.value.playerPreferences

    private val onWindowAttributesChangedListener =
        CopyOnWriteArrayList<Consumer<WindowManager.LayoutParams?>>()

    private var isPlaybackFinished = false
    private var hasPlaybackStarted = false
    private var playbackFailed = false
    private var lastDuration = C.TIME_UNSET
    private var lastPosition = C.TIME_UNSET
    private var lastMediaUri: Uri? = null
    private var lastDecoder: DecoderMode? = null
    private var playInBackground: Boolean = false
    private var isIntentNew: Boolean = true
    private var backgroundLaunchAccepted = false
    private var activityCreated = false
    private var backgroundControllerFuture: ListenableFuture<MediaController>? = null

    private var mediaController by mutableStateOf<MediaController?>(null)
    private var playerApi by mutableStateOf(PlayerApi(Intent()))
    private var playbackRequestJob: Job? = null

    private val playbackStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            intent.data = mediaItem?.localConfiguration?.uri
            isPlaybackFinished = false
            playbackFailed = false
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateKeepScreenOnFlag()
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackFailed = true
            if (playerApi.suppressErrorMessage) {
                val controller = mediaController ?: return
                if (controller.hasNextMediaItem()) {
                    controller.seekToNextMediaItem()
                    controller.prepare()
                } else {
                    finishAndStopPlayerSession()
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) hasPlaybackStarted = true
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

    private val audioFileSuspendLauncher =
        registerForSuspendActivityResult(contract = OpenDocumentAtInitialUri())

    private val subtitleFileSuspendLauncher =
        registerForSuspendActivityResult(contract = OpenDocumentAtInitialUri())

    override fun onCreate(savedInstanceState: Bundle?) {
        val request = PlayerApi(intent)
        if (request.launchInBackground) setTheme(R.style.NextPlayer_Background)
        super.onCreate(savedInstanceState)
        activityCreated = true
        playerApi = request
        if (request.launchInBackground && intent.data == null) {
            playbackFailed = true
            finish()
            return
        }
        isIntentNew = savedInstanceState?.getBoolean(
            "isIntentNew",
            true,
        ) ?: true
        hasPlaybackStarted = savedInstanceState?.getBoolean("hasPlaybackStarted") ?: false
        playbackFailed = savedInstanceState?.getBoolean("playbackFailed") ?: false
        lastDuration = savedInstanceState?.getLong(
            "lastDuration",
            C.TIME_UNSET,
        ) ?: C.TIME_UNSET
        lastPosition = savedInstanceState?.getLong(
            "lastPosition",
            C.TIME_UNSET,
        ) ?: C.TIME_UNSET
        lastMediaUri = savedInstanceState?.getString("lastMediaUri")?.let(Uri::parse)
        lastDecoder = savedInstanceState?.getString("lastDecoder")?.let { name ->
            DecoderMode.entries.find { it.name == name }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        viewModel.output = playerOutput()
        playerApi = PlayerApi(intent)
        if (playerApi.launchInBackground) {
            connectBackgroundController()
            return
        }
        setContent {
            val player = rememberMediaController(
                onBeforeRelease = ::onControllerStopped,
                onExtrasChanged = { extras ->
                    viewModel.onAction(
                        PlayerAction.UpdateDecoderServiceState(extras.decoderServiceState()),
                    )
                },
            )
            LaunchedEffect(key1 = player) {
                if (player == null || !player.isConnected) return@LaunchedEffect
                mediaController = player
                viewModel.onAction(
                    PlayerAction.UpdateDecoderServiceState(
                        player.sessionExtras.decoderServiceState(),
                    ),
                )
                if (!playerApi.launchInBackground) player.addListener(playbackStateListener)
                updateKeepScreenOnFlag()
                startPlayback()
            }

            if (!playerApi.launchInBackground) {
                NextPlayerTheme(darkTheme = true) {
                    MediaPlayerScreen(
                        viewModel = viewModel,
                        player = player,
                        api = playerApi,
                    )
                }
            }
        }
    }

    private fun connectBackgroundController() {
        val future = MediaController.Builder(
            applicationContext,
            SessionToken(
                applicationContext,
                ComponentName(
                    applicationContext,
                    PlayerService::class.java,
                ),
            ),
        ).buildAsync()
        backgroundControllerFuture = future
        lifecycleScope.launch {
            try {
                mediaController = future.await()
                startPlayback()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                playbackFailed = true
                finish()
            }
        }
    }

    override fun onDestroy() {
        backgroundControllerFuture?.let {
            MediaController.releaseFuture(it)
            mediaController = null
        }
        backgroundControllerFuture = null
        super.onDestroy()
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
            lifecycleScope.launch { mediaController?.setVideoDecoderMode(mode = mode) }
        },
        setAudioDecoderMode = { mode ->
            lifecycleScope.launch { mediaController?.setAudioDecoderMode(mode = mode) }
        },
        tryDecoderFallback = { lifecycleScope.launch { mediaController?.tryDecoderFallback() } },
    )

    private suspend fun currentMediaDirectory(): Uri? {
        val uri = mediaController?.currentMediaItem?.localConfiguration?.uri ?: return null
        return withContext(Dispatchers.IO) { getInitialDirectoryUri(fileUri = uri) }
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
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            snapshotFlow { mediaController }.filterNotNull().first().addSubtitleTrack(uri = uri)
        }
    }

    private fun selectAudio() {
        lifecycleScope.launch {
            val uri = audioFileSuspendLauncher.launch(
                OpenDocumentAtInitialUri.Input(
                    mimeTypes = arrayOf(
                        "audio/*",
                        "application/ogg",
                    ),
                    initialUri = currentMediaDirectory(),
                ),
            ) ?: return@launch
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                val controller = snapshotFlow { mediaController }.filterNotNull().first()
                if (!controller.addAudioTrack(uri = uri)) {
                    Toast.makeText(
                        this@PlayerActivity,
                        coreUiR.string.error_opening_audio,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (_: SecurityException) {
                Toast.makeText(
                    this@PlayerActivity,
                    coreUiR.string.error_opening_audio,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun onControllerStopped() {
        playbackRequestJob?.cancel()
        mediaController?.run {
            lastDuration = duration
            lastPosition = currentPosition
            lastMediaUri = currentMediaItem?.localConfiguration?.uri
            lastDecoder = sessionExtras.decoderServiceState().videoMode
            viewModel.onAction(PlayerAction.UpdatePlayWhenReady(playWhenReady))
            removeListener(playbackStateListener)
            val shouldPlayInBackground =
                playInBackground || (playerApi.sticky ?: playerPreferences.autoBackgroundPlay)
            if (subtitleFileSuspendLauncher.isAwaitingResult ||
                audioFileSuspendLauncher.isAwaitingResult ||
                !shouldPlayInBackground
            ) {
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
        val controller = mediaController ?: return
        if (!isIntentNew &&
            (controller.currentMediaItem == null || controller.playbackState == Player.STATE_ENDED)
        ) {
            // Completion can happen while the activity's playback listener is detached.
            isPlaybackFinished = true
            finishAndStopPlayerSession()
            return
        }
        val uri = intent.data ?: return

        val returningFromBackground = !isIntentNew
        val currentUri = controller.currentMediaItem?.localConfiguration?.uri
        val hasExplicitPlaylist =
            playerApi.isApiAccess || intent.hasExtra(PlayerApi.API_PLAYLIST) ||
                intent.hasExtra(PlaylistPlaybackContract.EXTRA_PLAYLIST_ID)
        isIntentNew = false

        if (shouldResumeExistingPlayback(
                returningFromBackground = returningFromBackground,
                isRequestedUriCurrent = currentUri.toString() == uri.toString(),
                hasExplicitPlaylist = hasExplicitPlaylist,
                hasNextMediaItem = controller.hasNextMediaItem(),
            )
        ) {
            controller.prepare()
            controller.playWhenReady = viewModel.state.value.playWhenReady
            return
        }

        playbackRequestJob?.cancel()
        playbackRequestJob = lifecycleScope.launch {
            playVideo(
                uri = uri,
                playlistId = intent.playlistIdOrNull(),
            )
        }
    }

    private suspend fun playVideo(uri: Uri, playlistId: Long?) = withContext(Dispatchers.Default) {
        val request = playerApi
        val savedQueue = playlistId
            ?.let { playlistRepository.getPlaylist(playlistId = it) }
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
                startQueue(
                    request,
                    mediaItems,
                    startIndex,
                    C.TIME_UNSET,
                )
            }
            return@withContext
        }

        val mediaContentUri = if (request.isApiAccess) null else getMediaContentUri(uri)
        val explicitPlaylist = request.getPlaylist()
        val playlist = explicitPlaylist.takeIf { it.isNotEmpty() }
            ?: mediaContentUri?.let { mediaUri ->
                viewModel.getPlaylistFromUri(uri = mediaUri)
                    .map { it.uriString }
                    .toMutableList()
                    .apply {
                        if (!contains(mediaUri.toString())) {
                            add(
                                index = 0,
                                element = mediaUri.toString(),
                            )
                        }
                    }
            } ?: listOf(uri.toString())

        val mediaItemIndexToPlay = playlist.indexOfFirst {
            it == (mediaContentUri ?: uri).toString()
        }.takeIf { it >= 0 } ?: 0

        val apiSubs = request.getSubs().map { subtitle ->
            uriToSubtitleConfiguration(
                uri = subtitle.uri,
                subtitleEncoding = playerPreferences.subtitleTextEncoding,
                isSelected = subtitle.isSelected,
                label = subtitle.name,
                filename = subtitle.filename,
                useMediaSourceForNetwork = true,
            )
        }
        val mediaItems = request.createMediaItems(
            playlist,
            mediaItemIndexToPlay,
            apiSubs,
        )
        ensureActive()

        withContext(Dispatchers.Main) {
            startQueue(
                request,
                mediaItems,
                mediaItemIndexToPlay,
                request.position?.toLong() ?: C.TIME_UNSET,
            )
        }
    }

    private suspend fun startQueue(
        request: PlayerApi,
        items: List<MediaItem>,
        index: Int,
        position: Long,
    ) {
        val controller = mediaController ?: return
        if (!request.launchInBackground) {
            controller.setMediaItems(
                items,
                index,
                position,
            )
            request.applyPlaybackSpeed(controller)
            controller.playWhenReady = viewModel.state.value.playWhenReady
            controller.prepare()
            return
        }
        // This explicit background launch must survive activity release even when sticky=false.
        playInBackground = true
        try {
            val result = controller.startBackgroundPlayback(
                items,
                index,
                position,
                request.playbackSpeed,
            )
            currentCoroutineContext().ensureActive()
            if (request !== playerApi) return
            if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                try {
                    retainPlaybackUriPermissions(
                        intent,
                        items,
                    )
                } catch (error: Exception) {
                    controller.stopPlayerSession()
                    throw error
                }
                backgroundLaunchAccepted = true
                playbackFailed = false
                lastMediaUri = result.extras.getString(
                    CustomCommands.BACKGROUND_MEDIA_URI_KEY,
                )?.let(Uri::parse)
                lastPosition = result.extras.getLong(
                    CustomCommands.BACKGROUND_POSITION_KEY,
                    C.TIME_UNSET,
                )
                lastDuration = result.extras.getLong(
                    CustomCommands.BACKGROUND_DURATION_KEY,
                    C.TIME_UNSET,
                )
            } else {
                playbackFailed = true
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            playbackFailed = true
        }
        finish()
    }

    private fun Intent.playlistIdOrNull(): Long? = getLongExtra(
        PlaylistPlaybackContract.EXTRA_PLAYLIST_ID,
        Long.MIN_VALUE,
    ).takeUnless { it == Long.MIN_VALUE }

    override fun finish() {
        if (playerApi.shouldReturnResult) {
            val result = playerApi.getResult(
                isPlaybackFinished = isPlaybackFinished,
                duration = if (playerApi.launchInBackground) {
                    lastDuration
                } else {
                    mediaController?.duration
                        ?: lastDuration
                },
                position = if (playerApi.launchInBackground) {
                    lastPosition
                } else {
                    mediaController?.currentPosition
                        ?: lastPosition
                },
                uri = if (playerApi.launchInBackground) {
                    lastMediaUri ?: intent.data
                } else {
                    mediaController?.currentMediaItem?.localConfiguration?.uri ?: lastMediaUri
                        ?: intent.data
                },
                usedDecoder =
                    if (playerApi.launchInBackground) {
                        null
                    } else {
                        mediaController?.sessionExtras?.decoderServiceState()?.videoMode
                            ?: lastDecoder
                    },
            )
            setResult(
                when {
                    playbackFailed -> RESULT_FIRST_USER
                    backgroundLaunchAccepted -> RESULT_OK
                    mediaController?.playerError != null -> RESULT_FIRST_USER
                    hasPlaybackStarted || isPlaybackFinished -> RESULT_OK
                    else -> RESULT_CANCELED
                },
                result,
            )
        }
        super.finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            val previousBackgroundMode = playerApi.launchInBackground
            playbackRequestJob?.cancel()
            setIntent(intent)
            playerApi = PlayerApi(intent)
            if (playerApi.launchInBackground) mediaController?.removeListener(playbackStateListener)
            isPlaybackFinished = false
            hasPlaybackStarted = false
            playbackFailed = false
            lastDuration = C.TIME_UNSET
            lastPosition = C.TIME_UNSET
            lastMediaUri = null
            lastDecoder = null
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            playInBackground = false
            backgroundLaunchAccepted = false
            isIntentNew = true
            if (activityCreated && previousBackgroundMode != playerApi.launchInBackground) {
                recreate()
            } else {
                startPlayback()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            "isIntentNew",
            isIntentNew,
        )
        outState.putBoolean(
            "hasPlaybackStarted",
            hasPlaybackStarted,
        )
        outState.putBoolean(
            "playbackFailed",
            playbackFailed,
        )
        outState.putLong(
            "lastDuration",
            mediaController?.duration ?: lastDuration,
        )
        outState.putLong(
            "lastPosition",
            mediaController?.currentPosition ?: lastPosition,
        )
        outState.putString(
            "lastMediaUri",
            (
                mediaController?.currentMediaItem?.localConfiguration?.uri
                    ?: lastMediaUri
                )?.toString(),
        )
        outState.putString(
            "lastDecoder",
            (
                mediaController?.sessionExtras?.decoderServiceState()?.videoMode
                    ?: lastDecoder
                )?.name,
        )
        super.onSaveInstanceState(outState)
    }

    private fun updateKeepScreenOnFlag() {
        if (!playerApi.launchInBackground && mediaController?.isPlaying == true) {
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
            listener.accept(value = params)
        }
    }

    fun addOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.add(listener)
    }

    fun removeOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.remove(listener)
    }
}
