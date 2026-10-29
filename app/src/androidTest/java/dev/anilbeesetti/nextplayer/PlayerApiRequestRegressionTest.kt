package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import java.util.UUID
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class PlayerApiRequestRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testPackage = instrumentation.context.packageName
    private val caller = "$testPackage/dev.anilbeesetti.nextplayer.api.ApiTestCallerActivity"
    private val token = UUID.randomUUID().toString()
    private val preferences get() = GlobalContext.get().get<PreferencesRepository>()
    private lateinit var original: PlayerPreferences
    private var controller: MediaController? = null

    @Before
    fun prepare() {
        original = preferences.playerPreferences.value
        runBlocking {
            preferences.updatePlayerPreferences {
                it.copy(
                    autoPip = false,
                    autoBackgroundPlay = false,
                    autoplay = true,
                    loopMode = LoopMode.OFF,
                )
            }
            withTimeout(10000) {
                preferences.playerPreferences.first { !it.autoPip && !it.autoBackgroundPlay }
            }
        }
    }

    @After
    fun clean() {
        command("release")
        main {
            activities().forEach { it.finish() }
            controller?.run {
                stop()
                clearMediaItems()
                release()
            }
        }
        context.stopService(
            Intent(
                context,
                PlayerService::class.java,
            ),
        )
        command("finish")
        runBlocking { preferences.updatePlayerPreferences { original } }
    }

    @Test
    fun backgroundQueueRetainsReadGrantsUntilTheServiceIsReleased() {
        launch("--ez background true --ez queue true")
        assertEquals(
            Activity.RESULT_OK,
            result().getInt("code"),
        )
        val player = connect()
        await("background playback after activity closes") {
            main { activities().isEmpty() && player.isPlaying && player.mediaItemCount == 3 }
        }
        val media = main { player.getMediaItemAt(2).localConfiguration!!.uri }
        val subtitle = main {
            player.getMediaItemAt(0).localConfiguration!!.subtitleConfigurations.single().uri
        }
        SystemClock.sleep(500)
        assertTrue(
            "Queued URI remains readable",
            canRead(media),
        )
        assertTrue(
            "Subtitle URI remains readable",
            canRead(subtitle),
        )
        main {
            player.seekTo(
                2,
                0,
            )
        }
        await("unbuffered queue item plays") {
            main {
                player.currentMediaItemIndex == 2 && player.playbackState == Player.STATE_READY &&
                    player.isPlaying && player.playerError == null
            }
        }
        main { player.stopPlayerSession() }
        await("session stops") { main { player.mediaItemCount == 0 } }
        main {
            player.release()
            controller = null
        }
        await("released service revokes media and subtitle grants") {
            !canRead(media) && !canRead(subtitle)
        }
    }

    private fun canRead(uri: Uri): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(
            uri,
            "r",
        )?.use { true } == true
    } catch (_: SecurityException) {
        false
    }

    @Test
    fun backgroundQueueWinsAfterCancelledForegroundMetadataReturns() {
        launch("--es gate $token")
        await("foreground metadata is blocked") { gateStatus() == 1 }
        val player = connect()
        command(
            "replace",
            "--es item second.wav --ez background true --ef speed 1.5",
        )
        assertEquals(
            Activity.RESULT_OK,
            result().getInt("code"),
        )
        await("background queue is playing") {
            main {
                player.currentMediaItem?.localConfiguration?.uri?.lastPathSegment == "second.wav" &&
                    player.isPlaying && player.playbackParameters.speed == 1.5f
            }
        }
        command("release")
        await("cancelled metadata returns") { gateStatus() == 2 }
        assertStable {
            assertEquals(
                "second.wav",
                player.currentMediaItem?.localConfiguration?.uri?.lastPathSegment,
            )
            assertTrue(player.isPlaying)
            assertEquals(
                1.5f,
                player.playbackParameters.speed,
            )
        }
    }

    @Test
    fun backDuringForegroundMetadataKeepsTheSessionStopped() {
        launch("--es gate $token")
        await("foreground metadata is blocked") { gateStatus() == 1 }
        val player = connect()
        main { activities().single().onBackPressedDispatcher.onBackPressed() }
        assertEquals(
            Activity.RESULT_CANCELED,
            result().getInt("code"),
        )
        command("release")
        await("cancelled metadata returns") { gateStatus() == 2 }
        assertStable {
            assertEquals(
                0,
                player.mediaItemCount,
            )
            assertFalse(player.isPlaying)
        }
    }

    private fun launch(options: String) {
        shell(
            "am start --activity-clear-task -n $caller --es token $token " +
                "--es target_package ${context.packageName} $options",
        )
    }

    private fun command(operation: String, options: String = ""): String = shell(
        "am broadcast -n $caller\$Commands --es operation $operation --es token $token $options",
    )

    private fun gateStatus(): Int = command("status").substringAfter(
        "result=",
        "-1",
    )
        .substringBefore(',').trim().toIntOrNull() ?: -1

    private fun result(): JSONObject {
        var result: JSONObject? = null
        await("external caller result") {
            result = runCatching {
                JSONObject(shell("run-as $testPackage cat files/api-result-$token.json"))
            }.getOrNull()
            result != null
        }
        return requireNotNull(result).also {
            assertFalse(
                it.toString(),
                it.has("caller_error"),
            )
            assertNotEquals(
                "Caller must run under another UID",
                Process.myUid(),
                it.getInt("callerUid"),
            )
        }
    }

    private fun connect(): MediaController {
        val future = main {
            MediaController.Builder(
                context,
                SessionToken(
                    context,
                    ComponentName(
                        context,
                        PlayerService::class.java,
                    ),
                ),
            )
                .buildAsync()
        }
        return future.get(
            10,
            TimeUnit.SECONDS,
        ).also { controller = it }
    }

    private fun activities(): List<PlayerActivity> = Stage.entries
        .filter { it != Stage.DESTROYED }
        .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
        .filterIsInstance<PlayerActivity>()

    private fun assertStable(assertions: () -> Unit) {
        val until = SystemClock.elapsedRealtime() + 1000
        do {
            main(assertions)
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < until)
    }

    private fun await(description: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        error("Timed out waiting for $description")
    }

    private fun <T> main(block: () -> T): T {
        val task = FutureTask(block)
        android.os.Handler(android.os.Looper.getMainLooper()).post(task)
        return task.get(
            10,
            TimeUnit.SECONDS,
        )
    }

    private fun shell(command: String): String = instrumentation.uiAutomation
        .executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader().use { it.readText() }
        }
}
