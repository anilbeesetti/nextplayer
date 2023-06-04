package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.libs.ffcodecs.FfmpegRenderersFactory
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getMediaContentUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.databinding.ActivityPlayerBinding
import dev.anilbeesetti.nextplayer.feature.player.dialogs.PlaybackSpeedSelectionDialogFragment
import dev.anilbeesetti.nextplayer.feature.player.dialogs.TrackSelectionDialogFragment
import dev.anilbeesetti.nextplayer.feature.player.dialogs.getCurrentTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.getSubs
import dev.anilbeesetti.nextplayer.feature.player.extensions.isRendererAvailable
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.toMediaItem
import dev.anilbeesetti.nextplayer.feature.player.extensions.toggleSystemBars
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerGestureHelper
import dev.anilbeesetti.nextplayer.feature.player.utils.PlaylistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@SuppressLint("UnsafeOptInUsageError")
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    lateinit var binding: ActivityPlayerBinding

    private val viewModel: PlayerViewModel by viewModels()

    /**
     * Intent data
     */
    private var intentDataUri: Uri? = null
    private var intentExtras: Bundle? = null
    private var intentType: String? = null

    private var playWhenReady = true
    private var isPlaybackFinished = false

    var isFileLoaded = false
    var isControlsLocked = false
    private var shouldFetchPlaylist = true
    private var isSubtitleLauncherHasUri = false
    private var isFirstFrameRendered = false
    private var currentOrientation: Int? = null

    /**
     * Player
     */
    private lateinit var player: Player
    private lateinit var playerGestureHelper: PlayerGestureHelper
    private lateinit var playlistManager: PlaylistManager
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var mediaSession: MediaSession

    /**
     * Listeners
     */
    private val playbackStateListener: Player.Listener = playbackStateListener()
    private val onTrackChangeListener: (Uri) -> Unit = { uri ->
        runOnUiThread {
            if (uri.toString() == intentDataUri.toString() && intentExtras?.containsKey(API_TITLE) == true) {
                videoTitleTextView.text = intentExtras?.getString(API_TITLE)
            } else {
                videoTitleTextView.text = getFilenameFromUri(uri)
            }
        }
    }
    private val subtitleFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                isSubtitleLauncherHasUri = true
                viewModel.currentExternalSubtitles.add(uri)
            }
            playVideo()
        }

    private lateinit var videoTitleTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        when (viewModel.appPrefs.value.themeConfig) {
            ThemeConfig.SYSTEM -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )

            ThemeConfig.OFF -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )

            ThemeConfig.ON -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        if (viewModel.appPrefs.value.useDynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        // The window is always allowed to extend into the DisplayCutout areas on the short edges of the screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateIntentData(intent)
        Timber.d("data: $intentDataUri")

        playerGestureHelper = PlayerGestureHelper(
            viewModel = viewModel,
            activity = this,
            playerView = binding.playerView,
            audioManager = getSystemService(android.media.AudioManager::class.java)
        )

        playlistManager = PlaylistManager()
    }

    override fun onStart() {
        createPlayer()
        playlistManager.addOnTrackChangedListener(onTrackChangeListener)
        preparePlayerView()
        initializePlayerView()
        playVideo()
        super.onStart()
    }

    override fun onStop() {
        binding.gestureVolumeLayout.visibility = View.GONE
        binding.gestureBrightnessLayout.visibility = View.GONE
        playlistManager.removeOnTrackChangedListener(onTrackChangeListener)
        currentOrientation = requestedOrientation
        releasePlayer()
        super.onStop()
    }

    private fun createPlayer() {
        Timber.d("Creating player")

        val renderersFactory = FfmpegRenderersFactory(application).setExtensionRendererMode(
            FfmpegRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )

        trackSelector = DefaultTrackSelector(applicationContext)

        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage(viewModel.preferences.value.preferredAudioLanguage)
                .setPreferredTextLanguage(viewModel.preferences.value.preferredSubtitleLanguage)
        )

        player = ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(getAudioAttributes(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(applicationContext, player).build()
        player.addListener(playbackStateListener)
    }

    private fun preparePlayerView() {
        binding.playerView.apply {
            player = this@PlayerActivity.player
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    toggleSystemBars(showBars = visibility == View.VISIBLE && !isControlsLocked)
                }
            )
        }
    }

    private fun initializePlayerView() {
        videoTitleTextView =
            binding.playerView.findViewById(R.id.video_name)

        val backButton =
            binding.playerView.findViewById<ImageButton>(R.id.back_button)
        val playbackSpeedButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_playback_speed)
        val audioTrackButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleTrackButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle_track)
        val videoZoomButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_video_zoom)
        val screenRotationButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_screen_rotation)
        val nextButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_play_next)
        val prevButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_play_prev)
        val lockControlsButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_lock_controls)
        val unlockControlsButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_unlock_controls)
        val playerControls =
            binding.playerView.findViewById<FrameLayout>(R.id.player_controls)

        binding.playerView.subtitleView?.setFixedTextSize(Cue.TEXT_SIZE_TYPE_ABSOLUTE, 24f)

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
                    MimeTypes.TEXT_SSA
                )
            )
            true
        }

        playbackSpeedButton.setOnClickListener {
            PlaybackSpeedSelectionDialogFragment(
                player = player
            ).show(supportFragmentManager, "PlaybackSpeedSelectionDialog")
        }

        nextButton.setOnClickListener {
            if (playlistManager.hasNext()) {
                playlistManager.getCurrent()?.let { savePlayerState(it) }
                playVideo(playlistManager.getNext()!!)
            }
        }
        prevButton.setOnClickListener {
            if (playlistManager.hasPrev()) {
                playlistManager.getCurrent()?.let { savePlayerState(it) }
                playVideo(playlistManager.getPrev()!!)
            }
        }
        videoZoomButton.setOnClickListener {
            binding.playerView.resizeMode =
                if (binding.playerView.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
        }
        lockControlsButton.setOnClickListener {
            playerControls.visibility = View.INVISIBLE
            unlockControlsButton.visibility = View.VISIBLE
            isControlsLocked = true
            toggleSystemBars(showBars = false)
        }
        unlockControlsButton.setOnClickListener {
            unlockControlsButton.visibility = View.INVISIBLE
            playerControls.visibility = View.VISIBLE
            isControlsLocked = false
            toggleSystemBars(showBars = true)
        }
        screenRotationButton.setOnClickListener { switchOrientation() }
        backButton.setOnClickListener { finish() }
    }

    private fun playVideo(uri: Uri) {
        playlistManager.updateCurrent(uri)
        playVideo()
    }

    private fun playVideo() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (shouldFetchPlaylist) {
                val mediaUri = getMediaContentUri(intentDataUri!!)

                Timber.d("content Uri: $mediaUri")

                playlistManager.updateCurrent(uri = mediaUri ?: intentDataUri!!)

                if (mediaUri != null) {
                    launch(Dispatchers.IO) {
                        val playlist = viewModel.getPlaylistFromUri(mediaUri)
                        playlistManager.setPlaylist(playlist)
                    }
                }

                shouldFetchPlaylist = false
            }

            getPath(playlistManager.getCurrent()!!)?.let {
                viewModel.updateState(
                    path = it,
                    shouldUpdateSubtitles = !isSubtitleLauncherHasUri
                )
            }
            if (isSubtitleLauncherHasUri) {
                viewModel.currentSubtitleTrackIndex = null
            }

            val subs = playlistManager.getCurrent()!!.getSubs(
                context = this@PlayerActivity,
                extras = intentExtras,
                externalSubtitles = viewModel.currentExternalSubtitles
            )

            val mediaItem = playlistManager.getCurrent()!!.toMediaItem(
                type = intentType,
                subtitles = subs
            )
            withContext(Dispatchers.Main) {
                player.setMediaItem(
                    mediaItem,
                    viewModel.currentPlaybackPosition ?: C.TIME_UNSET
                )
                player.playWhenReady = playWhenReady
                player.prepare()
            }
            isSubtitleLauncherHasUri = false
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

        @SuppressLint("SourceLockedOrientationActivity")
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            requestedOrientation = currentOrientation?.let {
                currentOrientation
            } ?: if (videoSize.isPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
                        playVideo(playlistManager.getNext()!!)
                    } else {
                        finish()
                    }
                }

                Player.STATE_READY -> {
                    Timber.d("Player state: READY")
                    Timber.d(
                        "Current: ${playlistManager.currentIndex()} ${playlistManager.getCurrent()}"
                    )
                    Timber.d(playlistManager.toString())
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
            super.onRenderedFirstFrame()
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (!isFirstFrameRendered) {
                player.switchTrack(C.TRACK_TYPE_AUDIO, viewModel.currentAudioTrackIndex)
                player.switchTrack(C.TRACK_TYPE_TEXT, viewModel.currentSubtitleTrackIndex)
                player.setPlaybackSpeed(viewModel.currentPlaybackSpeed)
            }
        }
    }

    override fun finish() {
        if (intentExtras != null && intentExtras!!.containsKey(API_RETURN_RESULT)) {
            val result = Intent(API_RESULT_INTENT)
            result.putExtra(API_END_BY, if (isPlaybackFinished) "playback_completion" else "user")
            if (!isPlaybackFinished) {
                player.also {
                    if (it.duration != C.TIME_UNSET) {
                        result.putExtra(API_DURATION, it.duration.toInt())
                    }
                    result.putExtra(API_POSITION, it.currentPosition.toInt())
                }
            }
            Timber.d("Sending result: $result")
            setResult(Activity.RESULT_OK, result)
        }
        super.finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            Timber.d("new intent: ${intent.data}")
            playlistManager.clearQueue()
            viewModel.resetToDefaults()
            updateIntentData(intent)
            shouldFetchPlaylist = true
            playVideo()
        }
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    private fun updateIntentData(intent: Intent) {
        intentDataUri = intent.data
        intentExtras = intent.extras
        intentType = intent.type

        if (intentExtras?.containsKey(API_POSITION) == true) {
            viewModel.currentPlaybackPosition = intentExtras?.getInt(API_POSITION)?.toLong()
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

    companion object {
        const val API_TITLE = "title"
        const val API_POSITION = "position"
        const val API_DURATION = "duration"
        const val API_RETURN_RESULT = "return_result"
        const val API_END_BY = "end_by"
        const val API_SUBS = "subs"
        const val API_SUBS_ENABLE = "subs.enable"
        const val API_SUBS_NAME = "subs.name"
        const val API_RESULT_INTENT = "com.mxtech.intent.result.VIEW"
    }
}

private val VideoSize.isPortrait: Boolean
    get() {
        val isRotated = this.unappliedRotationDegrees == 90 || this.unappliedRotationDegrees == 270
        return if (isRotated) this.width > this.height else this.height > this.width
    }

private fun Activity.switchOrientation() {
    val isLandscape = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
        requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    requestedOrientation = if (isLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    Timber.d("setting orientation $requestedOrientation")
}
