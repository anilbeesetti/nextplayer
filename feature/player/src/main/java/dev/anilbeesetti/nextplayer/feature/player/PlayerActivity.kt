package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.feature.player.databinding.ActivityPlayerBinding
import dev.anilbeesetti.nextplayer.feature.player.utils.hideSystemBars
import dev.anilbeesetti.nextplayer.feature.player.utils.showSystemBars
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "PlayerActivity"

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private lateinit var binding: ActivityPlayerBinding

    private val viewModel: PlayerViewModel by viewModels()

    private var videosList: List<String> = emptyList()
    private var player: Player? = null
    private var dataUri: Uri? = null
    private var playWhenReady = true
    private var playbackPosition = 0L

    private val playbackStateListener: Player.Listener = playbackStateListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        dataUri = intent.data

        dataUri?.let {
            if (it.scheme == "content") {
                viewModel.setCurrentMedia(viewModel.getPath(it))
                videosList = viewModel.getVideos()
            }
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackPosition.collectLatest {
                    if (it != null) {
                        player?.seekTo(it)
                    }
                }
            }
        }
    }

    override fun onStart() {
        initializePlayer()
        super.onStart()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { player ->
            binding.playerView.player = player
            binding.playerView.setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    when (visibility) {
                        View.VISIBLE -> {
                            this.showSystemBars()
                        }
                        View.GONE -> {
                            this.hideSystemBars()
                        }
                    }
                }
            )

            val currentMediaItemIndex =
                videosList.indexOfFirst { it == viewModel.currentPlaybackPath }

            if (currentMediaItemIndex != -1) {
                val mediaItems: MutableList<MediaItem> = mutableListOf()
                videosList.forEach {
                    val mediaItem = MediaItem.Builder()
                        .setUri(File(it).toUri())
                        .setMediaId(it)
                        .build()

                    mediaItems.add(mediaItem)
                }
                player.setMediaItems(mediaItems, currentMediaItemIndex, C.TIME_UNSET)
            } else {
                dataUri?.let { player.addMediaItem(MediaItem.fromUri(it)) }
                player.seekTo(playbackPosition)
            }

            player.playWhenReady = playWhenReady
            player.addListener(playbackStateListener)
            player.prepare()
        }
    }

    private fun releasePlayer() {
        player?.let { player ->
            playWhenReady = player.playWhenReady
            playbackPosition = player.currentPosition
            viewModel.updatePosition(player.currentPosition)
            player.removeListener(playbackStateListener)
            player.release()
        }
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
    }
}

private val VideoSize.isPortrait: Boolean
    get() {
        val isRotated = this.unappliedRotationDegrees == 90 || this.unappliedRotationDegrees == 270
        return if (isRotated) this.width > this.height else this.height > this.width
    }
