package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import java.util.concurrent.FutureTask
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

    @Test
    fun phoneDpadNavigationOnlyActsWithHiddenControlsAndBackStillExits() = withDpadPlayer(1) {
            player,
            _,
        ->
        waitForControls(visible = false)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        await("DPAD next item") { player.currentMediaItemIndex == 1 && player.isPlaying }
        waitForControls(visible = false)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        await("DPAD previous item") { player.currentMediaItemIndex == 0 && player.isPlaying }
        showControlsByTouch()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        onMain {
            assertEquals(
                0,
                player.currentMediaItemIndex,
            )
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back exits phone player with controls visible") { player.mediaItemCount == 0 }
    }

    @Test
    fun phoneDpadVolumeChangesDoNotChangeTheQueue() = withDpadPlayer(0) { player, _ ->
        val audio = instrumentation.targetContext.getSystemService(AudioManager::class.java)
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            audio.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                2,
                0,
            )
            waitForControls(visible = false)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            await("DPAD volume up") { audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 2 }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await("DPAD volume down") { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 2 }
            onMain {
                assertEquals(
                    0,
                    player.currentMediaItemIndex,
                )
            }
        } finally {
            audio.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                originalVolume,
                0,
            )
        }
    }

    @Test
    fun omittingDpadOptionRestoresPhoneKeyHandling() = withDpadPlayer(1) { player, activity ->
        waitForControls(visible = false)
        val uri = onMain { player.currentMediaItem!!.localConfiguration!!.uri }
        onMain {
            instrumentation.callActivityOnNewIntent(
                activity,
                request(uri)
                    .putExtra(
                        PlayerApi.API_TITLE,
                        "Default key handling",
                    )
                    .putExtra(
                        PlayerApi.API_PLAYLIST,
                        arrayOf(
                            uri,
                            uri,
                        ),
                    )
                    .putExtra(
                        "video_list_is_explicit",
                        true,
                    ),
            )
        }
        await("replacement request without DPAD option") {
            player.mediaMetadata.title == "Default key handling" &&
                player.currentMediaItemIndex == 0 && player.isPlaying
        }
        instrumentation.waitForIdleSync()
        waitForControls(visible = false)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        SystemClock.sleep(300)
        onMain {
            assertEquals(
                0,
                player.currentMediaItemIndex,
            )
        }
    }

    private fun withDpadPlayer(mode: Int, check: (MediaController, PlayerActivity) -> Unit) =
        withPlayer {
                connectPlayer,
                uri,
                preferences,
            ->
            runBlocking {
                preferences.updatePlayerPreferences { it.copy(controllerAutoHideTimeout = 1) }
                preferences.playerPreferences.first { it.controllerAutoHideTimeout == 1 }
            }
            val player = connectPlayer()
            val intent = request(uri)
                .putExtra(
                    "keys.dpad_up_down",
                    mode,
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
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val activity = instrumentation.startActivitySync(intent) as PlayerActivity
            try {
                await("phone API queue playing") { player.isPlaying && player.mediaItemCount == 2 }
                check(
                    player,
                    activity,
                )
            } finally {
                onMain { activity.finish() }
            }
        }

    private fun waitForControls(visible: Boolean) {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags =
                flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName.toString() == "com.android.systemui" &&
                root.findAccessibilityNodeInfosByViewId(
                    "com.android.systemui:id/immersive_cling_title",
                ).isNotEmpty()
            ) {
                root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/ok")
                    .single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                continue
            }
            if (root != null &&
                root.packageName.toString() == instrumentation.targetContext.packageName &&
                hasPlayPauseControl(root) == visible
            ) {
                return
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Player controls visibility did not become $visible")
    }

    private fun hasPlayPauseControl(node: AccessibilityNodeInfo): Boolean {
        val label = instrumentation.targetContext.getString(
            dev.anilbeesetti.nextplayer.core.ui.R.string.play_pause,
        )
        if (node.isVisibleToUser && node.contentDescription?.toString() == label) return true
        return (0 until node.childCount).any {
            node.getChild(it)?.let(::hasPlayPauseControl) == true
        }
    }

    private fun showControlsByTouch() {
        instrumentation.uiAutomation.waitForIdle(
            100,
            5000,
        )
        val bounds = Rect()
        instrumentation.uiAutomation.rootInActiveWindow.getBoundsInScreen(bounds)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP,
        )) {
            val event = MotionEvent.obtain(
                now,
                now,
                action,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                0,
            )
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        waitForControls(visible = true)
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

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(block)
        instrumentation.runOnMainSync(task)
        return task.get(
            10,
            TimeUnit.SECONDS,
        )
    }

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
