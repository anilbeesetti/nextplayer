package com.graviton.feature.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentResolver
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
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
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
import com.graviton.core.common.extensions.getInitialDirectoryUri
import com.graviton.core.common.extensions.getMediaContentUri
import com.graviton.core.common.service.registerForSuspendActivityResult
import com.graviton.core.data.stream.StreamExtractor
import com.graviton.core.model.ExtractedStream
import com.graviton.core.model.StreamUrls
import com.graviton.core.model.VideoPlayerBackend
import com.graviton.core.ui.theme.GravitonTheme
import com.graviton.feature.player.backend.MpvPlayerActivity
import com.graviton.feature.player.extensions.OpenDocumentAtInitialUri
import com.graviton.feature.player.extensions.setExtras
import com.graviton.feature.player.extensions.uriToSubtitleConfiguration
import com.graviton.feature.player.service.PlayerService
import com.graviton.feature.player.service.addSubtitleTrack
import com.graviton.feature.player.service.stopPlayerSession
import com.graviton.feature.player.ui.NetworkStreamDialog
import com.graviton.feature.player.utils.PlayerApi
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalUseMaterialYouControls = compositionLocalOf { false }

internal fun shouldResumeExistingPlayback(
    returningFromBackground: Boolean,
    isRequestedUriCurrent: Boolean,
    hasExplicitPlaylist: Boolean,
): Boolean = returningFromBackground || (isRequestedUriCurrent && !hasExplicitPlaylist)

@SuppressLint("UnsafeOptInUsageError")
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    val playerPreferences get() = viewModel.uiState.value.playerPreferences

    private val onWindowAttributesChangedListener = CopyOnWriteArrayList<Consumer<WindowManager.LayoutParams?>>()

    private var isPlaybackFinished = false
    private var playInBackground: Boolean = false
    private var isIntentNew: Boolean = true

    private val keepSessionOnClose: Boolean
        get() = intent.getBooleanExtra(PlayerApi.API_KEEP_SESSION, false)

    /**
     * Player
     */
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var controllerConnectionJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackGeneration: Long = 0L
    private var renderedController: MediaController? by mutableStateOf(null)
    private lateinit var playerApi: PlayerApi

    @javax.inject.Inject
    lateinit var streamExtractor: StreamExtractor

    /**
    * Listeners
     */
    private val playbackStateListener: Player.Listener = playbackStateListener()

    private val subtitleFileSuspendLauncher = registerForSuspendActivityResult(OpenDocumentAtInitialUri())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selectedBackend = viewModel.uiState.value.playerPreferences?.videoPlayerBackend
            ?: VideoPlayerBackend.GRAVITON
        if (selectedBackend != VideoPlayerBackend.GRAVITON && intent.data != null) {
            startActivity(
                Intent(intent).apply {
                    setClass(this@PlayerActivity, MpvPlayerActivity::class.java)
                    putExtra(MpvPlayerActivity.EXTRA_BACKEND, selectedBackend.name)
                },
            )
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // Initialise the API before any lifecycle callback can start playback. The view observes
        // renderedController, which is assigned only after the single MediaController future has
        // resolved; this prevents an empty/stale surface host during rapid navigation.
        playerApi = PlayerApi(this)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val player = renderedController
            var showNetworkStreamDialog by rememberSaveable { mutableStateOf(false) }

            CompositionLocalProvider(LocalUseMaterialYouControls provides (uiState.playerPreferences?.useMaterialYouControls == true)) {
                GravitonTheme(darkTheme = true) {
                    if (showNetworkStreamDialog) {
                        NetworkStreamDialog(
                            onPlay = { url ->
                                showNetworkStreamDialog = false
                                playNetworkStream(url)
                            },
                            onDismiss = { showNetworkStreamDialog = false },
                        )
                    }

                    MediaPlayerScreen(
                        player = player,
                        viewModel = viewModel,
                        uiState = uiState,
                        playerPreferences = uiState.playerPreferences ?: return@GravitonTheme,
                        onNetworkStreamClick = { showNetworkStreamDialog = true },
                        onShareClick = ::shareCurrentMedia,
                        onSettingsClick = ::openAppSettings,
                        onSelectSubtitleClick = {
                            lifecycleScope.launch {
                                val videoUri = mediaController?.currentMediaItem?.localConfiguration?.uri
                                val initialUri = videoUri?.let { video ->
                                    withContext(Dispatchers.IO) { getInitialDirectoryUri(video) }
                                }
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
                                        initialUri = initialUri,
                                    ),
                                ) ?: return@launch
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                maybeInitControllerFuture()
                                controllerFuture?.await()?.addSubtitleTrack(uri)
                            }
                        },
                        onBackClick = {
                            if (keepSessionOnClose) finish() else finishAndStopPlayerSession()
                        },
                        onPlayInBackgroundClick = {
                            playInBackground = true
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controllerConnectionJob?.cancel()
        controllerConnectionJob = lifecycleScope.launch {
            maybeInitControllerFuture()
            val controller = controllerFuture?.await() ?: return@launch
            // onStop may have happened while await() was suspended.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            mediaController = controller
            renderedController = controller
            updateKeepScreenOnFlag()
            controller.addListener(playbackStateListener)
            startPlayback()
        }
    }

    override fun onStop() {
        controllerConnectionJob?.cancel()
        controllerConnectionJob = null
        playbackJob?.cancel()
        playbackJob = null

        val controller = mediaController
        controller?.let {
            viewModel.playWhenReady = it.playWhenReady
            it.removeListener(playbackStateListener)
        }
        val shouldPlayInBackground = playInBackground || keepSessionOnClose || playerPreferences?.autoBackgroundPlay == true
        if (subtitleFileSuspendLauncher.isAwaitingResult || !shouldPlayInBackground) {
            controller?.pause()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            finish()
            if (!shouldPlayInBackground) controller?.stopPlayerSession()
        }

        // Detach the session from the old rendering target before releasing the controller. This
        // is the important lifecycle edge: a new Activity must not inherit a surface whose
        // BufferQueue belongs to the previous window.
        controller?.clearVideoSurface()
        renderedController = null
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
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
        val hasExplicitPlaylist = intent.hasExtra(PlayerApi.API_PLAYLIST)

        if (shouldResumeExistingPlayback(
                returningFromBackground = returningFromBackground,
                isRequestedUriCurrent = isNewUriTheCurrentMediaItem,
                hasExplicitPlaylist = hasExplicitPlaylist,
            )
        ) {
            mediaController?.prepare()
            mediaController?.playWhenReady = viewModel.playWhenReady
            return
        }

        isIntentNew = false

        playbackJob?.cancel()
        val requestGeneration = ++playbackGeneration
        playbackJob = lifecycleScope.launch {
            playVideo(uri, requestGeneration)
        }
    }

    private suspend fun playVideo(uri: Uri, requestGeneration: Long) = withContext(Dispatchers.Default) {
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

        val mediaItems = playlist.mapIndexed { index, playlistUri ->
            val extracted = resolveStream(playlistUri)
            MediaItem.Builder().apply {
                setUri(extracted.playableUrl)
                setMediaId(playlistUri)
                if (extracted.isHls) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                } else if (extracted.isDash) {
                    setMimeType(MimeTypes.APPLICATION_MPD)
                }
                setMediaMetadata(
                    MediaMetadata.Builder().apply {
                        setTitle(extracted.title ?: playerApi.title)
                        extracted.uploader?.let(::setArtist)
                        extracted.thumbnailUrl?.let { setArtworkUri(it.toUri()) }
                        if (index == mediaItemIndexToPlay) {
                            setExtras(positionMs = playerApi.position?.toLong())
                        }
                    }.build(),
                )
                if (index == mediaItemIndexToPlay) {
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
            if (requestGeneration != playbackGeneration || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@withContext
            mediaController?.run {
                setMediaItems(mediaItems, mediaItemIndexToPlay, playerApi.position?.toLong() ?: C.TIME_UNSET)
                playWhenReady = viewModel.playWhenReady
                prepare()
            }
        }
    }

    private suspend fun resolveStream(playlistUri: String): ExtractedStream {
        if (!StreamUrls.needsExtraction(playlistUri)) {
            return ExtractedStream(sourceUrl = playlistUri, playableUrl = playlistUri)
        }
        return runCatching { streamExtractor.resolve(playlistUri) }.getOrElse {
            ExtractedStream(sourceUrl = playlistUri, playableUrl = playlistUri)
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
            playerApi = PlayerApi(this)
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

    /**
     * Plays [url] in the session that is already running.
     *
     * Reusing the existing controller means the service, the surface and the notification are all
     * untouched: only the media item changes.
     */
    private fun playNetworkStream(url: String) {
        val controller = mediaController ?: return
        val uri = url.toUri()
        controller.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(url)
                .build(),
        )
        controller.playWhenReady = true
        controller.prepare()
        intent.data = uri
    }

    /** Shares the file currently playing through the system chooser. */
    private fun shareCurrentMedia() {
        val uri = mediaController?.currentMediaItem?.localConfiguration?.uri ?: return
        val shareIntent = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE -> Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Remote streams have no file to attach, so the URL itself is the shareable payload.
            else -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, uri.toString())
            }
        }
        runCatching { startActivity(Intent.createChooser(shareIntent, null)) }
    }

    /**
     * Opens the app's own settings.
     *
     * The launcher Activity is resolved from the package manager rather than referenced directly,
     * because `:feature:player` must not depend on `:app`.
     */
    private fun openAppSettings() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.putExtra(EXTRA_OPEN_SETTINGS, true)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(launchIntent) }
    }

    companion object {
        /** Set on the launcher intent so the main Activity opens Settings on arrival. */
        const val EXTRA_OPEN_SETTINGS = "com.graviton.extra.OPEN_SETTINGS"
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
