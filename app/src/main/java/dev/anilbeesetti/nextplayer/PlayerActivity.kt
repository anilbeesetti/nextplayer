package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.databinding.ActivityPlayerBinding

class PlayerActivity : ComponentActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: Player? = null
    private var data: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        data = intent.getStringExtra("data")
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
            data?.let { player.addMediaItem(MediaItem.fromUri(it)) }
            player.playWhenReady = true
            player.prepare()
        }
    }

    private fun releasePlayer() {
        player?.release()
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