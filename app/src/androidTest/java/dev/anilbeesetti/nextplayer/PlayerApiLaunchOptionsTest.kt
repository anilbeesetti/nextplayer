package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.navigation.startPlayback
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class PlayerApiLaunchOptionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun directBackgroundLaunchReturnsToCallerAndKeepsPlayingAtRequestedSpeed() = withPlayer {
            connectPlayer,
            uri,
            preferences,
        ->
        val intent = request(uri).putExtra(
            PlayerApi.API_BACKGROUND,
            true,
        )
            .putExtra(
                PlayerApi.API_PLAYBACK_SPEED,
                1.5f,
            )
            .putExtra(
                PlayerApi.API_POSITION,
                5000,
            )
            .putExtra(
                PlayerApi.API_RETURN_RESULT,
                true,
            )
            .putExtra(
                "sticky",
                false,
            )
        ActivityScenario.launchActivityForResult<PlayerActivity>(intent).use { scenario ->
            val result = scenario.result
            val player = connectPlayer()
            assertEquals(
                Activity.RESULT_OK,
                result.resultCode,
            )
            assertEquals(
                PlayerApi.API_RESULT_INTENT,
                result.resultData.action,
            )
            assertEquals(
                uri,
                result.resultData.data,
            )
            await("playback after background activity closes") {
                player.isPlaying && player.currentPosition >= 5000 &&
                    player.playbackParameters.speed == 1.5f
            }
            onMain {
                assertTrue(
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(
                        Stage.RESUMED,
                    ).none {
                        it is PlayerActivity
                    },
                )
                assertFalse(preferences.playerPreferences.value.autoBackgroundPlay)
                assertEquals(
                    1.25f,
                    preferences.playerPreferences.value.defaultPlaybackSpeed,
                )
            }
        }
    }

    @Test
    fun foregroundLaunchSetsSpeedOnceAndKeepsThePlayerScreen() = withPlayer {
            connectPlayer,
            uri,
            preferences,
        ->
        val player = connectPlayer()
        val intent = request(uri).putExtra(
            PlayerApi.API_PLAYBACK_SPEED,
            1.75f,
        )
            .putExtra(
                "video_list",
                arrayOf(
                    uri,
                    uri,
                ),
            )
            .putExtra(
                "video_list_is_explicit",
                true,
            )
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            await("foreground API playback speed") {
                player.isPlaying &&
                    player.playbackParameters.speed == 1.75f
            }
            scenario.onActivity { assertFalse(it.isFinishing) }
            onMain {
                player.setPlaybackSpeed(2f)
                player.seekToNextMediaItem()
            }
            await("playlist transition without resetting API speed") {
                player.currentMediaItemIndex == 1 && player.isPlaying &&
                    player.playbackParameters.speed == 2f
            }
            assertEquals(
                1.25f,
                preferences.playerPreferences.value.defaultPlaybackSpeed,
            )
        }
    }

    @Test
    fun backgroundLaunchResumesAPausedSessionWithoutResettingItsSpeed() = withPlayer {
            connectPlayer,
            uri,
            _,
        ->
        val player = connectPlayer()
        onMain {
            player.setPlaybackSpeed(2f)
            player.pause()
        }
        val intent = request(uri).putExtra(
            PlayerApi.API_BACKGROUND,
            true,
        )
            .putExtra(
                PlayerApi.API_RETURN_RESULT,
                true,
            )
        ActivityScenario.launchActivityForResult<PlayerActivity>(intent).use { scenario ->
            assertEquals(
                Activity.RESULT_OK,
                scenario.result.resultCode,
            )
            await("background launch of paused session") {
                player.isPlaying &&
                    player.playbackParameters.speed == 2f
            }
        }
    }

    @Test
    fun internalSelectedVideosContinueWhenAutoplayIsDisabled() = withPlayer {
            connectPlayer,
            uri,
            preferences,
        ->
        runBlocking {
            preferences.updatePlayerPreferences { it.copy(autoplay = false) }
            preferences.playerPreferences.first { !it.autoplay }
        }
        val player = connectPlayer()
        val picker = instrumentation.startActivitySync(
            Intent(
                instrumentation.targetContext,
                MainActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            onMain {
                picker.startPlayback(
                    uris = listOf(
                        uri,
                        uri,
                    ),
                )
            }
            await("selected queue playing") {
                player.isPlaying && player.mediaItemCount == 2 && player.duration > 0
            }
            onMain { player.seekTo(player.duration - 200) }
            await("selected queue continues with autoplay disabled") {
                player.currentMediaItemIndex == 1 && player.isPlaying
            }
        } finally {
            onMain {
                Stage.values().flatMap {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
                }.filterIsInstance<PlayerActivity>().filter { !it.isDestroyed }.forEach {
                    it.finish()
                }
                picker.finish()
            }
        }
    }

    private fun request(uri: Uri) = Intent(
        instrumentation.targetContext,
        PlayerActivity::class.java,
    ).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun withPlayer(check: (() -> MediaController, Uri, PreferencesRepository) -> Unit) {
        val context = instrumentation.targetContext
        val preferences = GlobalContext.get().get<PreferencesRepository>()
        val original = preferences.playerPreferences.value
        runBlocking {
            preferences.updatePlayerPreferences {
                it.copy(
                    autoBackgroundPlay = false,
                    autoPip = false,
                    defaultPlaybackSpeed = 1.25f,
                    loopMode = LoopMode.OFF,
                )
            }
        }
        runBlocking {
            withTimeout(30_000) {
                preferences.playerPreferences.first { value ->
                    !value.autoBackgroundPlay && !value.autoPip &&
                        value.defaultPlaybackSpeed == 1.25f
                }
            }
        }
        val audio = File(
            context.cacheDir,
            "api-launch-${System.nanoTime()}.wav",
        )
        val pcm = ByteArray(16_000 * 2 * 60)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size)
        audio.outputStream().use {
            it.write(header.array())
            it.write(pcm)
        }
        var player: MediaController? = null
        fun connectPlayer(): MediaController {
            player?.let { return it }
            lateinit var future: ListenableFuture<MediaController>
            onMain {
                future = MediaController.Builder(
                    context,
                    SessionToken(
                        context,
                        ComponentName(
                            context,
                            PlayerService::class.java,
                        ),
                    ),
                ).buildAsync()
            }
            return future.get(
                30,
                TimeUnit.SECONDS,
            ).also { player = it }
        }
        try {
            check(
                ::connectPlayer,
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    audio,
                ),
                preferences,
            )
        } finally {
            onMain {
                player?.stop()
                player?.clearMediaItems()
                player?.release()
            }
            context.stopService(
                Intent(
                    context,
                    PlayerService::class.java,
                ),
            )
            audio.delete()
            runBlocking { preferences.updatePlayerPreferences { original } }
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            onMain { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
