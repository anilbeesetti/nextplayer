package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.R as UiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.expandedPlaybackIntent
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.state.PlaylistState
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
        assertQueueGrantsSurviveActivity(player)
    }

    @Test
    fun backgroundStopReleasesExternalGrantsWithoutDestroyingMain() =
        checkBackgroundGrantRelease(dismiss = false)

    @Test
    fun expiredDismissalReleasesExternalGrantsWithoutDestroyingMain() =
        checkBackgroundGrantRelease(dismiss = true)

    private fun checkBackgroundGrantRelease(dismiss: Boolean) {
        launch("--ez background true --ez queue true")
        val player = connect()
        await("background queue ready") {
            main { player.mediaItemCount == 3 && player.playbackState == Player.STATE_READY }
        }
        val media = main { player.getMediaItemAt(2).localConfiguration!!.uri }
        val subtitle = main {
            player.getMediaItemAt(0).localConfiguration!!.subtitleConfigurations.single().uri
        }
        main {
            player.pause()
            player.seekTo(12_000)
        }
        ActivityScenario.launch<MainActivity>(context.expandedPlaybackIntent()).use { scenario ->
            await("expanded controls ready") {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                root?.packageName?.toString() == context.packageName && hasWatchVideoControl(root)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            val command = if (dismiss) {
                CustomCommands.DISMISS_PLAYBACK
            } else {
                CustomCommands.STOP_PLAYER_SESSION
            }
            val result = main {
                player.sendCustomCommand(
                    command.sessionCommand,
                    Bundle.EMPTY,
                )
            }
            assertEquals(
                0,
                result.get(
                    10,
                    TimeUnit.SECONDS,
                ).resultCode,
            )
            if (dismiss) {
                assertTrue(
                    "Undo retains queued URI",
                    canRead(media),
                )
                assertTrue(
                    "Undo retains subtitle URI",
                    canRead(subtitle),
                )
            }
            main {
                player.release()
                controller = null
            }
            await("background session releases media and subtitle grants") {
                !canRead(media) && !canRead(subtitle)
            }
            assertEquals(
                "Main must remain backgrounded",
                Lifecycle.State.CREATED,
                scenario.state,
            )
        }
    }

    @Test
    fun externalQueueHandoffReturnsResultAndRetainsGrantsForExpandedControls() {
        launch("--ez queue true")
        val player = connect()
        await("external queue accepted") {
            main {
                activities().isNotEmpty() && player.mediaItemCount == 3 &&
                    player.playbackState == Player.STATE_READY
            }
        }
        main {
            player.pause()
            player.seekTo(12_000)
        }
        await("paused seek reaches fullscreen controller") {
            main {
                val activity = activities().first()
                val peer = activity.javaClass.getDeclaredMethod("getMediaController")
                    .apply { isAccessible = true }.invoke(activity) as MediaController
                peer.playWhenReady == false && peer.currentPosition == 12_000L
            }
        }
        main {
            activities().first().let { activity ->
                activity.javaClass.getDeclaredMethod("openExpandedPlayback").apply {
                    isAccessible =
                        true
                }
                    .invoke(activity)
            }
        }
        val returned = result()
        assertEquals(
            Activity.RESULT_OK,
            returned.getInt("code"),
        )
        assertEquals(
            12_000,
            returned.getInt("position"),
        )
        assertTrue(returned.getString("uri").contains("first.wav"))
        await("expanded controls activity") {
            main {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is MainActivity }
            }
        }
        await("external handoff leaves video activity") { main { activities().isEmpty() } }
        main {
            assertFalse(player.playWhenReady)
            assertEquals(
                12_000,
                player.currentPosition,
            )
        }
        main {
            player.sendCustomCommand(
                CustomCommands.DISMISS_PLAYBACK.sessionCommand,
                Bundle.EMPTY,
            )
        }
            .get(
                10,
                TimeUnit.SECONDS,
            )
        await("dismissed external queue") { main { player.mediaItemCount == 0 } }
        assertTrue(
            "URI grant survives the Undo window",
            canRead(Uri.parse(returned.getString("uri"))),
        )
        main {
            player.sendCustomCommand(
                CustomCommands.UNDO_DISMISSAL.sessionCommand,
                Bundle.EMPTY,
            )
        }
            .get(
                10,
                TimeUnit.SECONDS,
            )
        await("paused external queue restored") {
            main {
                player.mediaItemCount == 3 && !player.playWhenReady &&
                    player.currentPosition == 12_000L
            }
        }
        val removed = main { player.getMediaItemAt(1) }
        val queue = main { PlaylistState(player).also { it.removeItem(1) } }
        await("external item removed") { main { player.mediaItemCount == 2 } }
        assertTrue(
            "Removed URI remains readable during queue Undo",
            canRead(removed.localConfiguration!!.uri),
        )
        val undone = main {
            queue.javaClass.declaredMethods.first { it.name.startsWith("undoRemoval") }
                .invoke(queue) as Boolean
        }
        assertTrue(undone)
        await("external queue item restored") {
            main {
                player.mediaItemCount == 3 && player.getMediaItemAt(1).mediaId == removed.mediaId
            }
        }
        main {
            assertFalse(player.playWhenReady)
            assertEquals(
                12_000L,
                player.currentPosition,
            )
            assertEquals(
                removed.localConfiguration,
                player.getMediaItemAt(1).localConfiguration,
            )
        }
        // Release the browsing controller before asserting service-owned grant revocation.
        main {
            Stage.entries.filter { it != Stage.DESTROYED }.flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
            }.filterIsInstance<MainActivity>().forEach { it.finish() }
        }
        main { player.play() }
        assertQueueGrantsSurviveActivity(player)
    }

    @Test
    fun foregroundQueueRetainsReadGrantsAfterActivityCloses() {
        runBlocking { preferences.updatePlayerPreferences { it.copy(autoBackgroundPlay = true) } }
        launch("--ez queue true --es gate $token")
        await("foreground queue metadata is blocked") { gateStatus() == 1 }
        val player = connect()
        command("release")
        await("foreground queue is playing") {
            main { activities().isNotEmpty() && player.isPlaying && player.mediaItemCount == 3 }
        }
        main { activities().forEach { it.finish() } }
        await("foreground activity closes while playback continues") {
            main { activities().isEmpty() && player.isPlaying }
        }
        assertQueueGrantsSurviveActivity(player)
    }

    private fun assertQueueGrantsSurviveActivity(player: MediaController) {
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

    private fun hasWatchVideoControl(node: AccessibilityNodeInfo): Boolean {
        val label = context.getString(UiR.string.watch_video)
        if (node.isVisibleToUser &&
            (node.text?.contains(label) == true || node.contentDescription?.contains(label) == true)
        ) {
            return true
        }
        return (0 until node.childCount).any {
            node.getChild(it)?.let(::hasWatchVideoControl) == true
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
