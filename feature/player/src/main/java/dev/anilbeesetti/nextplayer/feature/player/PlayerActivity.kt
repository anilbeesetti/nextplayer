package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.libs.ffcodecs.FfmpegRenderersFactory
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.databinding.ActivityPlayerBinding
import dev.anilbeesetti.nextplayer.feature.player.dialogs.TrackSelectionFragment
import dev.anilbeesetti.nextplayer.feature.player.extensions.isRendererAvailable
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.toMediaItem
import dev.anilbeesetti.nextplayer.feature.player.extensions.toggleSystemBars
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerGestureHelper
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("UnsafeOptInUsageError")
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    lateinit var binding: ActivityPlayerBinding

    private val viewModel: PlayerViewModel by viewModels()

    private var playerGestureHelper: PlayerGestureHelper? = null
    private var mediaSession: MediaSession? = null

    private var player: Player? = null
    private var dataUri: Uri? = null
    private var playWhenReady = true

    private val playbackStateListener: Player.Listener = playbackStateListener()
    private val trackSelector: DefaultTrackSelector by lazy {
        DefaultTrackSelector(
            applicationContext
        )
    }

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

        dataUri = intent.data

        Timber.d("data: $dataUri")

        dataUri?.let { viewModel.initMedia(getPath(it)) }

        val backButton =
            binding.playerView.findViewById<ImageButton>(R.id.back_button)
        val videoTitleTextView =
            binding.playerView.findViewById<TextView>(R.id.video_name)
        val audioTrackButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleTrackButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle_track)
        val nextButton =
            binding.playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_next)
        val prevButton =
            binding.playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_prev)

        videoTitleTextView.text = dataUri?.let { getFilenameFromUri(it) }

        // Collecting flows from view model
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playbackPosition.collectLatest { position ->
                        if (position != null && position != C.TIME_UNSET) {
                            Timber.d("Setting position: $position")
                            player?.seekTo(position)
                        }
                    }
                }

                launch {
                    viewModel.currentAudioTrackIndex.collectLatest { audioTrackIndex ->
                        player?.switchTrack(C.TRACK_TYPE_AUDIO, audioTrackIndex)
                    }
                }

                launch {
                    viewModel.currentSubtitleTrackIndex.collectLatest { subtitleTrackIndex ->
                        player?.switchTrack(C.TRACK_TYPE_TEXT, subtitleTrackIndex)
                    }
                }

                launch {
                    viewModel.currentPlaybackPath.collectLatest {
                        if (it != null) {
                            videoTitleTextView.text = File(it).name
                        }
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

        audioTrackButton.setOnClickListener {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return@setOnClickListener
            if (!mappedTrackInfo.isRendererAvailable(C.TRACK_TYPE_AUDIO)) return@setOnClickListener

            player?.let {
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

            player?.let {
                TrackSelectionFragment(
                    type = C.TRACK_TYPE_TEXT,
                    tracks = it.currentTracks,
                    viewModel = viewModel
                ).show(supportFragmentManager, "TrackSelectionDialog")
            }
        }

        nextButton.setOnClickListener {
            player?.currentPosition?.let { position -> viewModel.saveState(position) }
            player?.seekToNext()
        }
        prevButton.setOnClickListener {
            player?.currentPosition?.let { position -> viewModel.saveState(position) }
            player?.seekToPrevious()
        }
        backButton.setOnClickListener { finish() }
    }

    override fun onStart() {
        initializePlayer()
        super.onStart()
    }

    override fun onStop() {
        binding.gestureVolumeLayout.visibility = View.GONE
        binding.gestureBrightnessLayout.visibility = View.GONE
        releasePlayer()
        super.onStop()
    }

    private fun initializePlayer() {
        Timber.d("Initializing player")
        val renderersFactory = FfmpegRenderersFactory(application).setExtensionRendererMode(
            FfmpegRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )

        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setPreferredTextLanguage("en")
        )
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .build()
            .also { player ->
                binding.playerView.player = player
                binding.playerView.setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        this.toggleSystemBars(showBars = visibility == View.VISIBLE)
                    }
                )

                mediaSession = MediaSession.Builder(this, player).build()

                if (viewModel.currentPlayerItemIndex != -1) {
                    val mediaItems = viewModel.currentPlayerItems.map { it.toMediaItem() }
                    player.setMediaItems(mediaItems, viewModel.currentPlayerItemIndex, C.TIME_UNSET)
                } else {
                    dataUri?.let { player.addMediaItem(MediaItem.fromUri(it)) }
                }

                player.playWhenReady = playWhenReady
                player.addListener(playbackStateListener)
                player.prepare()
            }
    }

    private fun releasePlayer() {
        Timber.d("Releasing player")
        player?.let { player ->
            playWhenReady = player.playWhenReady
            viewModel.saveState(player.currentPosition)
            player.removeListener(playbackStateListener)
            player.release()
        }
        mediaSession?.release()
        player = null
        mediaSession = null
    }

    private fun playbackStateListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.playerView.keepScreenOn = isPlaying
        }

        @SuppressLint("SourceLockedOrientationActivity")
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            requestedOrientation = if (videoSize.isPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            viewModel.setCurrentMedia(mediaItem?.mediaId)
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                oldPosition.mediaItem?.let { viewModel.saveState(it.mediaId, C.TIME_UNSET) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Timber.e(error)
            val alertDialog = MaterialAlertDialogBuilder(this@PlayerActivity)
                .setTitle(getString(coreUiR.string.error_playing_video))
                .setMessage(error.message ?: getString(coreUiR.string.unknown_error))
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener {
                    if (player?.hasNextMediaItem() == true) player?.seekToNext() else finish()
                }
                .create()

            alertDialog.show()
        }

        override fun onTracksChanged(tracks: Tracks) {
            player?.switchTrack(C.TRACK_TYPE_AUDIO, viewModel.currentAudioTrackIndex.value)
            player?.switchTrack(C.TRACK_TYPE_TEXT, viewModel.currentSubtitleTrackIndex.value)
            super.onTracksChanged(tracks)
        }
    }
}

private val VideoSize.isPortrait: Boolean
    get() {
        val isRotated = this.unappliedRotationDegrees == 90 || this.unappliedRotationDegrees == 270
        return if (isRotated) this.width > this.height else this.height > this.width
    }
