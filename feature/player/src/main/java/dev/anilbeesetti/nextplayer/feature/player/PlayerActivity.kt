package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.annotation.Dimension
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.common.extensions.clearCache
import dev.anilbeesetti.nextplayer.core.common.extensions.convertToUTF8
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getMediaContentUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.model.DecoderPriority
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.databinding.ActivityPlayerBinding
import dev.anilbeesetti.nextplayer.feature.player.dialogs.PlaybackSpeedControlsDialogFragment
import dev.anilbeesetti.nextplayer.feature.player.dialogs.TrackSelectionDialogFragment
import dev.anilbeesetti.nextplayer.feature.player.dialogs.VideoZoomOptionsDialogFragment
import dev.anilbeesetti.nextplayer.feature.player.dialogs.nameRes
import dev.anilbeesetti.nextplayer.feature.player.extensions.audioSessionId
import dev.anilbeesetti.nextplayer.feature.player.extensions.getCurrentTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.getLocalSubtitles
import dev.anilbeesetti.nextplayer.feature.player.extensions.getSubtitleMime
import dev.anilbeesetti.nextplayer.feature.player.extensions.isPortrait
import dev.anilbeesetti.nextplayer.feature.player.extensions.isRendererAvailable
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.prettyPrintIntent
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekBack
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekForward
import dev.anilbeesetti.nextplayer.feature.player.extensions.setImageDrawable
import dev.anilbeesetti.nextplayer.feature.player.extensions.shouldFastSeek
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.toActivityOrientation
import dev.anilbeesetti.nextplayer.feature.player.extensions.toSubtitle
import dev.anilbeesetti.nextplayer.feature.player.extensions.toTypeface
import dev.anilbeesetti.nextplayer.feature.player.extensions.toggleSystemBars
import dev.anilbeesetti.nextplayer.feature.player.model.Subtitle
import dev.anilbeesetti.nextplayer.feature.player.utils.BrightnessManager
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerGestureHelper
import dev.anilbeesetti.nextplayer.feature.player.utils.PlaylistManager
import dev.anilbeesetti.nextplayer.feature.player.utils.VolumeManager
import dev.anilbeesetti.nextplayer.feature.player.utils.toMillis
import io.github.anilbeesetti.nextlib.ffcodecs.NextRenderersFactory
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@SuppressLint("UnsafeOptInUsageError")
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    lateinit var binding: ActivityPlayerBinding

    private val viewModel: PlayerViewModel by viewModels()
    private val currentContext = this
    private val applicationPreferences get() = viewModel.appPrefs.value
    private val playerPreferences get() = viewModel.playerPrefs.value

    private var playWhenReady = true
    private var isPlaybackFinished = false

    var isFileLoaded = false
    var isControlsLocked = false
    private var shouldFetchPlaylist = true
    private var isSubtitleLauncherHasUri = false
    private var isFirstFrameRendered = false
    private var isFrameRendered = false
    private var previousScrubPosition = 0L
    private var isPlayingOnScrubStart: Boolean = false
    private var currentOrientation: Int? = null
    private var currentVideoOrientation: Int? = null
    var currentVideoSize: VideoSize? = null
    private var hideVolumeIndicatorJob: Job? = null
    private var hideBrightnessIndicatorJob: Job? = null

    private val shouldFastSeek: Boolean
        get() = playerPreferences.shouldFastSeek(player.duration)

    /**
     * Player
     */
    private lateinit var player: Player
    private lateinit var playerGestureHelper: PlayerGestureHelper
    private lateinit var playlistManager: PlaylistManager
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var mediaSession: MediaSession
    private lateinit var playerApi: PlayerApi
    private lateinit var volumeManager: VolumeManager
    private lateinit var brightnessManager: BrightnessManager
    var loudnessEnhancer: LoudnessEnhancer? = null

    /**
     * Listeners
     */
    private val playbackStateListener: Player.Listener = playbackStateListener()
    private val subtitleFileLauncher = registerForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            isSubtitleLauncherHasUri = true
            viewModel.externalSubtitles.add(uri)
        }
        playVideo()
    }

    /**
     * Player controller views
     */
    private lateinit var audioTrackButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var exoContentFrameLayout: AspectRatioFrameLayout
    private lateinit var lockControlsButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var playbackSpeedButton: ImageButton
    private lateinit var playerLockControls: FrameLayout
    private lateinit var playerUnlockControls: FrameLayout
    private lateinit var playerCenterControls: LinearLayout
    private lateinit var prevButton: ImageButton
    private lateinit var screenRotationButton: ImageButton
    private lateinit var seekBar: TimeBar
    private lateinit var subtitleTrackButton: ImageButton
    private lateinit var unlockControlsButton: ImageButton
    private lateinit var videoTitleTextView: TextView
    private lateinit var videoZoomButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prettyPrintIntent()

        AppCompatDelegate.setDefaultNightMode(
            when (applicationPreferences.themeConfig) {
                ThemeConfig.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeConfig.OFF -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeConfig.ON -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )

        if (applicationPreferences.useDynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        // The window is always allowed to extend into the DisplayCutout areas on the short edges of the screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initializing views
        audioTrackButton = binding.playerView.findViewById(R.id.btn_audio_track)
        backButton = binding.playerView.findViewById(R.id.back_button)
        exoContentFrameLayout = binding.playerView.findViewById(R.id.exo_content_frame)
        lockControlsButton = binding.playerView.findViewById(R.id.btn_lock_controls)
        nextButton = binding.playerView.findViewById(R.id.btn_play_next)
        playbackSpeedButton = binding.playerView.findViewById(R.id.btn_playback_speed)
        playerLockControls = binding.playerView.findViewById(R.id.player_lock_controls)
        playerUnlockControls = binding.playerView.findViewById(R.id.player_unlock_controls)
        playerCenterControls = binding.playerView.findViewById(R.id.player_center_controls)
        prevButton = binding.playerView.findViewById(R.id.btn_play_prev)
        screenRotationButton = binding.playerView.findViewById(R.id.btn_screen_rotation)
        seekBar = binding.playerView.findViewById(R.id.exo_progress)
        subtitleTrackButton = binding.playerView.findViewById(R.id.btn_subtitle_track)
        unlockControlsButton = binding.playerView.findViewById(R.id.btn_unlock_controls)
        videoTitleTextView = binding.playerView.findViewById(R.id.video_name)
        videoZoomButton = binding.playerView.findViewById(R.id.btn_video_zoom)

        playerCenterControls.doOnLayout {
            binding.infoLayout.updatePaddingRelative(bottom = it.measuredHeight)
        }

        seekBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                if (player.isPlaying) {
                    isPlayingOnScrubStart = true
                    player.pause()
                }
                isFrameRendered = true
                previousScrubPosition = player.currentPosition
                scrub(position)
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                scrub(position)
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                if (isPlayingOnScrubStart) {
                    player.play()
                }
            }
        })

        volumeManager = VolumeManager(audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        brightnessManager = BrightnessManager(activity = this)
        playerGestureHelper = PlayerGestureHelper(
            viewModel = viewModel,
            activity = this,
            volumeManager = volumeManager,
            brightnessManager = brightnessManager
        )

        playlistManager = PlaylistManager()
        playerApi = PlayerApi(this)
    }

    override fun onStart() {
        if (playerPreferences.rememberPlayerBrightness) {
            brightnessManager.setBrightness(playerPreferences.playerBrightness)
        }
        createPlayer()
        setOrientation()
        initializePlayerView()
        playVideo()
        super.onStart()
    }

    override fun onStop() {
        binding.volumeGestureLayout.visibility = View.GONE
        binding.brightnessGestureLayout.visibility = View.GONE
        currentOrientation = requestedOrientation
        releasePlayer()
        super.onStop()
    }

    private fun createPlayer() {
        Timber.d("Creating player")

        val renderersFactory = NextRenderersFactory(applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (playerPreferences.decoderPriority) {
                    DecoderPriority.DEVICE_ONLY -> NextRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderPriority.PREFER_DEVICE -> NextRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderPriority.PREFER_APP -> NextRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )

        trackSelector = DefaultTrackSelector(applicationContext).apply {
            this.setParameters(
                this.buildUponParameters()
                    .setPreferredAudioLanguage(playerPreferences.preferredAudioLanguage)
                    .setPreferredTextLanguage(playerPreferences.preferredSubtitleLanguage)
            )
        }

        player = ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(getAudioAttributes(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(applicationContext, player).build()
        player.addListener(playbackStateListener)
        loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
        volumeManager.loudnessEnhancer = loudnessEnhancer
    }

    private fun setOrientation() {
        requestedOrientation = currentOrientation
            ?: playerPreferences.playerScreenOrientation.toActivityOrientation()
    }

    private fun initializePlayerView() {
        binding.playerView.apply {
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            player = this@PlayerActivity.player
            controllerShowTimeoutMs = playerPreferences.controllerAutoHideTimeout.toMillis
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    toggleSystemBars(showBars = visibility == View.VISIBLE && !isControlsLocked)
                }
            )

            subtitleView?.let {
                val style = CaptionStyleCompat(
                    Color.WHITE,
                    Color.BLACK.takeIf { playerPreferences.subtitleBackground } ?: Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                    Color.BLACK,
                    Typeface.create(
                        playerPreferences.subtitleFont.toTypeface(),
                        Typeface.BOLD.takeIf { playerPreferences.subtitleTextBold } ?: Typeface.NORMAL
                    )
                )
                it.setStyle(style)
                it.setApplyEmbeddedStyles(playerPreferences.applyEmbeddedStyles)
                it.setFixedTextSize(Dimension.SP, playerPreferences.subtitleTextSize.toFloat())
            }
        }

        audioTrackButton.setOnClickListener {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return@setOnClickListener
            if (!mappedTrackInfo.isRendererAvailable(C.TRACK_TYPE_AUDIO)) return@setOnClickListener

            TrackSelectionDialogFragment(
                type = C.TRACK_TYPE_AUDIO,
                tracks = player.currentTracks,
                onTrackSelected = { player.switchTrack(C.TRACK_TYPE_AUDIO, it) }
            ).show(supportFragmentManager, "TrackSelectionDialog")
        }

        subtitleTrackButton.setOnClickListener {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return@setOnClickListener
            if (!mappedTrackInfo.isRendererAvailable(C.TRACK_TYPE_TEXT)) return@setOnClickListener

            TrackSelectionDialogFragment(
                type = C.TRACK_TYPE_TEXT,
                tracks = player.currentTracks,
                onTrackSelected = { player.switchTrack(C.TRACK_TYPE_TEXT, it) }
            ).show(supportFragmentManager, "TrackSelectionDialog")
        }

        subtitleTrackButton.setOnLongClickListener {
            subtitleFileLauncher.launch(
                arrayOf(
                    MimeTypes.APPLICATION_SUBRIP,
                    MimeTypes.APPLICATION_TTML,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.BASE_TYPE_APPLICATION + "/octet-stream",
                    MimeTypes.BASE_TYPE_TEXT + "/*"
                )
            )
            true
        }

        playbackSpeedButton.setOnClickListener {
            PlaybackSpeedControlsDialogFragment(
                currentSpeed = player.playbackParameters.speed,
                onChange = {
                    viewModel.isPlaybackSpeedChanged = true
                    player.setPlaybackSpeed(it)
                }
            ).show(supportFragmentManager, "PlaybackSpeedSelectionDialog")
        }

        nextButton.setOnClickListener {
            if (playlistManager.hasNext()) {
                playlistManager.getCurrent()?.let { savePlayerState(it) }
                viewModel.resetAllToDefaults()
                playVideo(playlistManager.getNext()!!)
            }
        }
        prevButton.setOnClickListener {
            if (playlistManager.hasPrev()) {
                playlistManager.getCurrent()?.let { savePlayerState(it) }
                viewModel.resetAllToDefaults()
                playVideo(playlistManager.getPrev()!!)
            }
        }
        lockControlsButton.setOnClickListener {
            playerUnlockControls.visibility = View.INVISIBLE
            playerLockControls.visibility = View.VISIBLE
            isControlsLocked = true
            toggleSystemBars(showBars = false)
        }
        unlockControlsButton.setOnClickListener {
            playerLockControls.visibility = View.INVISIBLE
            playerUnlockControls.visibility = View.VISIBLE
            isControlsLocked = false
            toggleSystemBars(showBars = true)
        }
        videoZoomButton.setOnClickListener {
            val videoZoom = playerPreferences.playerVideoZoom.next()
            applyVideoZoom(videoZoom = videoZoom, showInfo = true)
        }

        videoZoomButton.setOnLongClickListener {
            VideoZoomOptionsDialogFragment(
                currentVideoZoom = playerPreferences.playerVideoZoom,
                onVideoZoomOptionSelected = { applyVideoZoom(videoZoom = it, showInfo = true) }
            ).show(supportFragmentManager, "VideoZoomOptionsDialog")
            true
        }
        screenRotationButton.setOnClickListener {
            requestedOrientation = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        screenRotationButton.setOnLongClickListener {
            playerPreferences.playerScreenOrientation.also {
                requestedOrientation = it.toActivityOrientation(currentVideoOrientation)
            }
            true
        }
        backButton.setOnClickListener { finish() }
    }

    private fun playVideo(uri: Uri? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (shouldFetchPlaylist) {
                val mediaUri = getMediaContentUri(intent.data!!)
                playlistManager.updateCurrent(uri = mediaUri ?: intent.data!!)

                if (mediaUri != null) {
                    launch(Dispatchers.IO) {
                        val playlist = viewModel.getPlaylistFromUri(mediaUri)
                        playlistManager.setPlaylist(playlist)
                    }
                }
                shouldFetchPlaylist = false
            }

            uri?.let { playlistManager.updateCurrent(uri) }

            val currentUri = playlistManager.getCurrent()!!

            viewModel.updateState(getPath(currentUri))
            if (intent.data == currentUri && playerApi.hasPosition) {
                viewModel.currentPlaybackPosition = playerApi.position?.toLong()
            }

            // Get all subtitles for current uri
            val apiSubs = if (intent.data == currentUri) playerApi.getSubs() else emptyList()
            val localSubs = currentUri.getLocalSubtitles(currentContext)
            val externalSubs = viewModel.externalSubtitles.map { it.toSubtitle(currentContext) }

            // current uri as MediaItem with subs
            val subtitleStreams = createExternalSubtitleStreams(apiSubs + localSubs + externalSubs)
            val mediaStream = createMediaStream(currentUri).buildUpon()
                .setSubtitleConfigurations(subtitleStreams)
                .build()

            withContext(Dispatchers.Main) {
                // Set api title if current uri is intent uri and intent extras contains api title
                if (intent.data == currentUri && playerApi.hasTitle) {
                    videoTitleTextView.text = playerApi.title
                } else {
                    videoTitleTextView.text = getFilenameFromUri(currentUri)
                }

                Timber.d("position: ${viewModel.currentPlaybackPosition}")
                // Set media and start player
                player.setMediaItem(mediaStream, viewModel.currentPlaybackPosition ?: C.TIME_UNSET)
                player.playWhenReady = playWhenReady
                player.prepare()
            }
        }
    }

    private fun releasePlayer() {
        Timber.d("Releasing player")
        playWhenReady = player.playWhenReady
        playlistManager.getCurrent()?.let { savePlayerState(it) }
        player.removeListener(playbackStateListener)
        player.release()
        mediaSession.release()
    }

    private fun playbackStateListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.playerView.keepScreenOn = isPlaying
            super.onIsPlayingChanged(isPlaying)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)
            loudnessEnhancer?.release()

            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @SuppressLint("SourceLockedOrientationActivity")
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            currentVideoSize = videoSize
            applyVideoZoom(videoZoom = playerPreferences.playerVideoZoom, showInfo = false)

            if (currentOrientation != null) return

            if (playerPreferences.playerScreenOrientation == ScreenOrientation.VIDEO_ORIENTATION) {
                currentVideoOrientation = if (videoSize.isPortrait) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                requestedOrientation = currentVideoOrientation!!
            }
            super.onVideoSizeChanged(videoSize)
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error)
            val alertDialog = MaterialAlertDialogBuilder(this@PlayerActivity)
                .setTitle(getString(coreUiR.string.error_playing_video))
                .setMessage(error.message ?: getString(coreUiR.string.unknown_error))
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener {
                    if (playlistManager.hasNext()) playVideo(playlistManager.getNext()!!) else finish()
                }
                .create()

            alertDialog.show()
            super.onPlayerError(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    Timber.d("Player state: ENDED")
                    isPlaybackFinished = true
                    if (playlistManager.hasNext()) {
                        playlistManager.getCurrent()?.let { savePlayerState(it) }
                        if (playerPreferences.autoplay) {
                            playVideo(playlistManager.getNext()!!)
                        }
                    } else {
                        finish()
                    }
                }

                Player.STATE_READY -> {
                    Timber.d("Player state: READY")
                    Timber.d(playlistManager.toString())
                    isFrameRendered = true
                    isFileLoaded = true
                }

                Player.STATE_BUFFERING -> {
                    Timber.d("Player state: BUFFERING")
                }

                Player.STATE_IDLE -> {
                    Timber.d("Player state: IDLE")
                }
            }
            super.onPlaybackStateChanged(playbackState)
        }

        override fun onRenderedFirstFrame() {
            isFirstFrameRendered = true
            binding.playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            super.onRenderedFirstFrame()
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            if (isFirstFrameRendered) return

            if (isSubtitleLauncherHasUri) {
                val textTracks = player.currentTracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                viewModel.currentSubtitleTrackIndex = textTracks.size - 1
            }
            isSubtitleLauncherHasUri = false
            player.switchTrack(C.TRACK_TYPE_AUDIO, viewModel.currentAudioTrackIndex)
            player.switchTrack(C.TRACK_TYPE_TEXT, viewModel.currentSubtitleTrackIndex)
            player.setPlaybackSpeed(viewModel.currentPlaybackSpeed)
        }
    }

    override fun finish() {
        clearCache()
        if (playerApi.shouldReturnResult) {
            val result = playerApi.getResult(
                isPlaybackFinished = isPlaybackFinished,
                duration = player.duration,
                position = player.currentPosition
            )
            setResult(Activity.RESULT_OK, result)
        }
        super.finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            playlistManager.clearQueue()
            viewModel.resetAllToDefaults()
            setIntent(intent)
            prettyPrintIntent()
            shouldFetchPlaylist = true
            playVideo()
        }
    }

    override fun setRequestedOrientation(requestedOrientation: Int) {
        super.setRequestedOrientation(requestedOrientation)
        screenRotationButton.setImageDrawable(this, getRotationDrawable())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeManager.increaseVolume()
                showVolumeGestureLayout()
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeManager.decreaseVolume()
                showVolumeGestureLayout()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                hideVolumeGestureLayout()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    private fun scrub(position: Long) {
        if (isFrameRendered) {
            isFrameRendered = false
            if (position > previousScrubPosition) {
                player.seekForward(position, shouldFastSeek)
            } else {
                player.seekBack(position, shouldFastSeek)
            }
            previousScrubPosition = position
        }
    }

    fun showVolumeGestureLayout() {
        hideVolumeIndicatorJob?.cancel()
        with(binding) {
            volumeGestureLayout.visibility = View.VISIBLE
            volumeProgressBar.max = volumeManager.maxVolume.times(100)
            volumeProgressBar.progress = volumeManager.currentVolume.times(100).toInt()
            volumeProgressText.text = volumeManager.volumePercentage.toString()
        }
    }

    fun showBrightnessGestureLayout() {
        hideBrightnessIndicatorJob?.cancel()
        with(binding) {
            brightnessGestureLayout.visibility = View.VISIBLE
            brightnessProgressBar.max = brightnessManager.maxBrightness.times(100).toInt()
            brightnessProgressBar.progress = brightnessManager.currentBrightness.times(100).toInt()
            brightnessProgressText.text = brightnessManager.brightnessPercentage.toString()
        }
    }

    fun hideVolumeGestureLayout(delayTimeMillis: Long = HIDE_DELAY_MILLIS) {
        if (binding.volumeGestureLayout.visibility != View.VISIBLE) return
        hideVolumeIndicatorJob = lifecycleScope.launch {
            delay(delayTimeMillis)
            binding.volumeGestureLayout.visibility = View.GONE
        }
    }

    fun hideBrightnessGestureLayout(delayTimeMillis: Long = HIDE_DELAY_MILLIS) {
        if (binding.brightnessGestureLayout.visibility != View.VISIBLE) return
        hideBrightnessIndicatorJob = lifecycleScope.launch {
            delay(delayTimeMillis)
            binding.brightnessGestureLayout.visibility = View.GONE
        }
        if (playerPreferences.rememberPlayerBrightness) {
            viewModel.setPlayerBrightness(window.attributes.screenBrightness)
        }
    }

    private fun savePlayerState(uri: Uri) {
        if (isFirstFrameRendered) {
            viewModel.saveState(
                path = getPath(uri),
                position = player.currentPosition,
                duration = player.duration,
                audioTrackIndex = player.getCurrentTrackIndex(C.TRACK_TYPE_AUDIO),
                subtitleTrackIndex = player.getCurrentTrackIndex(C.TRACK_TYPE_TEXT),
                playbackSpeed = player.playbackParameters.speed
            )
        }
        isFirstFrameRendered = false
    }

    private fun createMediaStream(uri: Uri) = MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .build()

    private fun createExternalSubtitleStreams(subtitles: List<Subtitle>): List<MediaItem.SubtitleConfiguration> {
        return subtitles.map {
            val charset = Charset.forName(playerPreferences.subtitleTextEncoding).takeIf {
                with(playerPreferences.subtitleTextEncoding) { isNotEmpty() && Charset.isSupported(this) }
            }
            MediaItem.SubtitleConfiguration.Builder(
                convertToUTF8(
                    uri = it.uri,
                    charset = charset
                )
            ).apply {
                setId(it.uri.toString())
                setMimeType(it.uri.getSubtitleMime())
                setLabel(it.name)
                if (it.isSelected) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            }.build()
        }
    }

    private fun resetExoContentFrameWidthAndHeight() {
        exoContentFrameLayout.layoutParams.width = LayoutParams.MATCH_PARENT
        exoContentFrameLayout.layoutParams.height = LayoutParams.MATCH_PARENT
        exoContentFrameLayout.scaleX = 1.0f
        exoContentFrameLayout.scaleY = 1.0f
        exoContentFrameLayout.requestLayout()
    }

    private fun applyVideoZoom(videoZoom: VideoZoom, showInfo: Boolean) {
        viewModel.setVideoZoom(videoZoom)
        resetExoContentFrameWidthAndHeight()
        when (videoZoom) {
            VideoZoom.BEST_FIT -> {
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                videoZoomButton.setImageDrawable(this, coreUiR.drawable.ic_fit_screen)
            }

            VideoZoom.STRETCH -> {
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                videoZoomButton.setImageDrawable(this, coreUiR.drawable.ic_aspect_ratio)
            }

            VideoZoom.CROP -> {
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                videoZoomButton.setImageDrawable(this, coreUiR.drawable.ic_crop_landscape)
            }

            VideoZoom.HUNDRED_PERCENT -> {
                currentVideoSize?.let {
                    exoContentFrameLayout.layoutParams.width = it.width
                    exoContentFrameLayout.layoutParams.height = it.height
                    exoContentFrameLayout.requestLayout()
                }
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                videoZoomButton.setImageDrawable(this, coreUiR.drawable.ic_width_wide)
            }
        }
        if (showInfo) {
            lifecycleScope.launch {
                binding.infoLayout.visibility = View.VISIBLE
                binding.infoText.text = getString(videoZoom.nameRes())
                delay(HIDE_DELAY_MILLIS)
                binding.infoLayout.visibility = View.GONE
            }
        }
    }

    companion object {
        const val HIDE_DELAY_MILLIS = 1000L
    }
}

private fun Activity.getRotationDrawable(): Int {
    return when (requestedOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> coreUiR.drawable.ic_portrait

        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> coreUiR.drawable.ic_landscape

        else -> coreUiR.drawable.ic_screen_rotation
    }
}
