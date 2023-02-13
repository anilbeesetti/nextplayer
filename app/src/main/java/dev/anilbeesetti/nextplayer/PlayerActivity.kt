package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.anilbeesetti.nextplayer.databinding.ActivityPlayerBinding

class PlayerActivity : ComponentActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: Player? = null
    private var data: Uri? = null
    private var playWhenReady = true
    private var playbackPosition = 0L

    private val playbackStateListener: Player.Listener = playbackStateListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        data = intent.data
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
            data?.let { player.addMediaItem(MediaItem.fromUri(it)) }

            player.playWhenReady = playWhenReady
            player.seekTo(playbackPosition)
            player.addListener(playbackStateListener)
            player.prepare()
        }
    }

    private fun releasePlayer() {
        player?.let { player ->
            playWhenReady = player.playWhenReady
            playbackPosition = player.currentPosition
            player.removeListener(playbackStateListener)
            player.release()
        }
    }

    private fun playbackStateListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.playerView.keepScreenOn = isPlaying
        }
    }
}

/**
 * Hide system bars
 */
fun Activity.hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Show system bars
 */
fun Activity.showSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        show(WindowInsetsCompat.Type.systemBars())
    }
}
