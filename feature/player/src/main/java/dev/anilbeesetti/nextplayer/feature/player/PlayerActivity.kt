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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.libs.ffcodecs.FfmpegRenderersFactory
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.databinding.ActivityPlayerBinding
import dev.anilbeesetti.nextplayer.feature.player.dialogs.PlaybackSpeedSelectionFragment
import dev.anilbeesetti.nextplayer.feature.player.dialogs.TrackSelectionFragment
import dev.anilbeesetti.nextplayer.feature.player.extensions.isRendererAvailable
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.toMediaItem
import dev.anilbeesetti.nextplayer.feature.player.extensions.toggleSystemBars
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerGestureHelper
import dev.anilbeesetti.nextplayer.feature.player.utils.Playlist
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
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

    /**
     * Player
     */
    private lateinit var player: Player
    private lateinit var playerGestureHelper: PlayerGestureHelper
    private lateinit var playlist: Playlist
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var mediaSession: MediaSession

    /**
     * Listeners
     */
    private val playbackStateListener: Player.Listener = playbackStateListener()
    private val onTrackChangeListener: (PlayerItem) -> Unit = {
        videoTitleTextView.text = File(it.path).name
        viewModel.updateInfo(it)
    }

    private lateinit var videoTitleTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // The window is always allowed to extend into the DisplayCutout areas on the short edges of the screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intentDataUri = intent.data
        intentExtras = intent.extras
        intentType = intent.type

        Timber.d("data: $intentDataUri")

        // Collecting flows from view model
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playbackPosition.collectLatest { position ->
                        if (position != null && position != C.TIME_UNSET) {
                            Timber.d("Setting position: $position")
                            player.seekTo(position)
                        }
                    }
                }
                launch {
                    viewModel.currentAudioTrackIndex.collectLatest { audioTrackIndex ->
                        player.switchTrack(C.TRACK_TYPE_AUDIO, audioTrackIndex)
                    }
                }

                launch {
                    viewModel.currentSubtitleTrackIndex.collectLatest { subtitleTrackIndex ->
                        player.switchTrack(C.TRACK_TYPE_TEXT, subtitleTrackIndex)
                    }
                }

                launch {
                    viewModel.currentPlaybackSpeed.collectLatest { playbackSpeed ->
                        player.setPlaybackSpeed(playbackSpeed)
                    }
                }
            }
        }

        playerGestureHelper = PlayerGestureHelper(
            viewModel = viewModel,
            activity = this,
            playerView = binding.playerView,
            audioManager = getSystemService(android.media.AudioManager::class.java)
        )

        playlist = Playlist()
    }

    override fun onStart() {
        createPlayer()
        playlist.addOnTrackChangedListener(onTrackChangeListener)
        preparePlayerView()
        initializePlayerView()
        playVideo()
        super.onStart()
    }

    override fun onStop() {
        binding.gestureVolumeLayout.visibility = View.GONE
        binding.gestureBrightnessLayout.visibility = View.GONE
        playlist.removeOnTrackChangedListener(onTrackChangeListener)
        releasePlayer()
        super.onStop()
    }

    private fun createPlayer() {
        Timber.d("Creating player")

        val renderersFactory = FfmpegRenderersFactory(application).setExtensionRendererMode(
            FfmpegRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )

        trackSelector = DefaultTrackSelector(applicationContext)

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

        audioTrackButton.setOnClickListener {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return@setOnClickListener
            if (!mappedTrackInfo.isRendererAvailable(C.TRACK_TYPE_AUDIO)) return@setOnClickListener

            player.let {
                TrackSelectionFragment(
                    type = C.TRACK_TYPE_AUDIO,
                    tracks = it.currentTracks,
                    viewModel = viewModel
                ).show(supportFragmentManager, "TrackSelectionDialog")
            }
        }

        subtitleTrackButton.setOnClickListener {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return@setOnClickListener
            if (!mappedTrackInfo.isRendererAvailable(C.TRACK_TYPE_TEXT)) return@setOnClickListener

            player.let {
                TrackSelectionFragment(
                    type = C.TRACK_TYPE_TEXT,
                    tracks = it.currentTracks,
                    viewModel = viewModel
                ).show(supportFragmentManager, "TrackSelectionDialog")
            }
        }

        playbackSpeedButton.setOnClickListener {
            PlaybackSpeedSelectionFragment(
                viewModel = viewModel
            ).show(supportFragmentManager, "PlaybackSpeedSelectionDialog")
        }

        nextButton.setOnClickListener {
            if (playlist.hasNext()) {
                viewModel.saveState(playlist.getCurrent(), player.currentPosition)
                playVideo(playlist.getNext()!!)
            }
        }
        prevButton.setOnClickListener {
            if (playlist.hasPrev()) {
                viewModel.saveState(playlist.getCurrent(), player.currentPosition)
                playVideo(playlist.getPrev()!!)
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
        backButton.setOnClickListener { finish() }
    }

    private fun playVideo(item: PlayerItem) {
        player.setMediaItem(item.toMediaItem(this@PlayerActivity, null))
        playlist.updateCurrent(item)
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    private fun playVideo() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (shouldFetchPlaylist) {
                val path = getPath(intentDataUri!!)
                val playerItem = viewModel.getPlayerItemFromPath(path)

                if (playerItem != null) {
                    playlist.updateCurrent(playerItem)
                    launch(Dispatchers.IO) {
                        val playerItems = viewModel.getPlayerItemsFromPath(path)
                        playlist.setPlayerItems(playerItems)
                    }
                }

                shouldFetchPlaylist = false
            }

            withContext(Dispatchers.Main) {
                if (playlist.getCurrent() == null) {
                    player.setMediaItem(
                        intentDataUri!!.toMediaItem(
                            context = this@PlayerActivity,
                            type = intentType,
                            extras = intentExtras
                        )
                    )

                    if (intentExtras?.containsKey(API_TITLE) == true) {
                        videoTitleTextView.text = intentExtras?.getString(API_TITLE)
                    } else {
                        videoTitleTextView.text = intentDataUri?.let { getFilenameFromUri(it) }
                    }

                    if (intentExtras?.containsKey(API_POSITION) == true) {
                        player.seekTo(intentExtras?.getInt(API_POSITION)?.toLong() ?: C.TIME_UNSET)
                    }
                } else {
                    player.setMediaItem(
                        playlist.getCurrent()!!.toMediaItem(
                            context = this@PlayerActivity,
                            type = intentType
                        ),
                        viewModel.playbackPosition.value ?: C.TIME_UNSET
                    )
                }
                player.playWhenReady = playWhenReady
                player.prepare()
            }
        }
    }

    private fun releasePlayer() {
        Timber.d("Releasing player")
        playWhenReady = player.playWhenReady
        viewModel.saveState(playlist.getCurrent(), player.currentPosition)
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
            requestedOrientation = if (videoSize.isPortrait) {
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
                    if (playlist.hasNext()) playVideo(playlist.getNext()!!) else finish()
                }
                .create()

            alertDialog.show()
            super.onPlayerError(error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            player.switchTrack(C.TRACK_TYPE_AUDIO, viewModel.currentAudioTrackIndex.value)
            player.switchTrack(C.TRACK_TYPE_TEXT, viewModel.currentSubtitleTrackIndex.value)
            super.onTracksChanged(tracks)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    Timber.d("Player state: ENDED")
                    isPlaybackFinished = true
                    if (playlist.hasNext()) {
                        viewModel.saveState(playlist.getCurrent(), C.TIME_UNSET)
                        playVideo(playlist.getNext()!!)
                    } else {
                        finish()
                    }
                }

                Player.STATE_READY -> {
                    Timber.d("Player state: READY")
                    Timber.d(playlist.toString())
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
            playlist.clearQueue()
            intentDataUri = intent.data
            intentExtras = intent.extras
            intentType = intent.type
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
