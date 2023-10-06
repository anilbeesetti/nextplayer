package dev.anilbeesetti.nextplayer.feature.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.DecoderPriority
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.feature.player.notification.NextNotificationProvider
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@UnstableApi
@AndroidEntryPoint
class PlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @Inject
    @Dispatcher(NextDispatchers.Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var playerPreferencesRepository: PreferencesRepository

    @Inject
    lateinit var nextNotificationProvider: NextNotificationProvider

    private val coroutineScope by lazy { CoroutineScope(mainDispatcher + SupervisorJob()) }

    fun get() = playerPreferencesRepository.playerPreferences.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferences()
    )

    private lateinit var playerPreferences: PlayerPreferences

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        playerPreferences = get().value

        setMediaNotificationProvider(nextNotificationProvider)
        val renderersFactory = NextRenderersFactory(applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (playerPreferences.decoderPriority) {
                    DecoderPriority.DEVICE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderPriority.PREFER_DEVICE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderPriority.PREFER_APP -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )

        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(playerPreferences.preferredAudioLanguage)
                    .setPreferredTextLanguage(playerPreferences.preferredSubtitleLanguage)
            )
        }

        val player = ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(getAudioAttributes(), playerPreferences.requireAudioFocus)
            .setHandleAudioBecomingNoisy(playerPreferences.pauseOnHeadsetDisconnect)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        nextNotificationProvider.cancelCoroutineScope()
        super.onDestroy()
    }
}
