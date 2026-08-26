package com.graviton.feature.player.backend

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.graviton.core.data.stream.StreamExtractor
import com.graviton.core.model.StreamUrls
import com.graviton.core.model.VideoPlayerBackend
import com.graviton.feature.player.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MpvPlayerActivity : ComponentActivity() {
    @Inject lateinit var streamExtractor: StreamExtractor

    private lateinit var mpvView: GravitonMpvView
    private lateinit var backend: MpvVideoBackend
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mpv_player)
        val kind = intent.getStringExtra(EXTRA_BACKEND)
            ?.let { runCatching { VideoPlayerBackend.valueOf(it) }.getOrNull() }
            ?.takeIf { it != VideoPlayerBackend.GRAVITON }
            ?: VideoPlayerBackend.MPV_REX
        mpvView = findViewById<GravitonMpvView>(R.id.mpv_surface).apply { backendKind = kind }
        File(filesDir, "mpv").mkdirs()
        mpvView.initialize(File(filesDir, "mpv").absolutePath, cacheDir.absolutePath)
        backend = MpvVideoBackend(kind)

        val requested = intent.data ?: run { finish(); return }
        lifecycleScope.launch {
            val playable = resolvePlayableUri(requested) ?: run { finish(); return@launch }
            if (!destroyed) backend.load(playable)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) runCatching { backend.pause() }
    }

    override fun onDestroy() {
        destroyed = true
        if (::mpvView.isInitialized) runCatching { mpvView.destroy() }
        super.onDestroy()
    }

    private suspend fun resolvePlayableUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val source = uri.toString()
                if (StreamUrls.needsExtraction(source)) runCatching { streamExtractor.resolve(source).playableUrl }.getOrNull() else source
            }
            "file" -> uri.path
            "content" -> runCatching {
                val fd = contentResolver.openFileDescriptor(uri, "r")?.detachFd() ?: return@runCatching null
                // libmpv's fd:// protocol takes ownership after loadfile.
                "fd://$fd"
            }.getOrNull()
            else -> uri.toString()
        }
    }

    companion object {
        const val EXTRA_BACKEND = "graviton.video.BACKEND"
    }
}
