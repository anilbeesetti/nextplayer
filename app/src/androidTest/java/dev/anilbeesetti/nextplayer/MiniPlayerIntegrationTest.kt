package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class MiniPlayerIntegrationTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var player: MediaController
    private lateinit var future: ListenableFuture<MediaController>
    private lateinit var media: File
    private lateinit var originalPreferences: PlayerPreferences
    private val preferences get() = GlobalContext.get().get<PreferencesRepository>()

    @Before
    fun startPlayback() {
        originalPreferences = preferences.playerPreferences.value
        runBlocking {
            preferences.updatePlayerPreferences {
                it.copy(
                    autoBackgroundPlay = false,
                    autoPip = false,
                    autoplay = true,
                    loopMode = LoopMode.OFF,
                )
            }
        }
        media = File(
            context.cacheDir,
            "Mini player ${System.nanoTime()}.wav",
        )
        val pcm = ByteArray(16_000 * 2 * 120)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size)
        media.outputStream().use {
            it.write(header.array())
            it.write(pcm)
        }
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
        player = future.get(
            15,
            TimeUnit.SECONDS,
        )
        onMain {
            player.setMediaItems(
                listOf(
                    MediaItem.Builder().setMediaId(Uri.fromFile(media).toString())
                        .setUri(Uri.fromFile(media)).build(),
                ),
                0,
                10_000,
            )
            player.prepare()
            player.play()
        }
        await("background playback") { player.isPlaying && player.duration > 0 }
        waitForMiniPlayer()
    }

    @After
    fun cleanUp() {
        onMain {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<PlayerActivity>().forEach { it.finish() }
            if (::player.isInitialized && player.isConnected) player.stopPlayerSession()
            if (::future.isInitialized) MediaController.releaseFuture(future)
        }
        if (::originalPreferences.isInitialized) {
            runBlocking { preferences.updatePlayerPreferences { originalPreferences } }
        }
        if (::media.isInitialized) media.delete()
    }

    @Test
    fun backgroundBarSurvivesEveryHomeTabAndRecreation() {
        listOf(
            R.string.playlists,
            R.string.network,
            R.string.more,
            R.string.home,
        ).forEach { label ->
            composeRule.onNodeWithContentDescription(context.getString(label)).performClick()
            composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        }
        composeRule.activityRule.scenario.recreate()
        waitForMiniPlayer()
        onMain { assertTrue(player.isPlaying) }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.pause_playback),
        ).performClick()
        await("pause from mini player") { !player.playWhenReady }
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.play)).performClick()
        await("resume from mini player") { player.isPlaying }
    }

    @Test
    fun tappingBarOpensCurrentQueueWithoutResettingPausedPosition() {
        onMain {
            player.pause()
            player.seekTo(30_000)
        }
        await("paused seek") { !player.playWhenReady && player.currentPosition == 30_000L }
        expand()
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.watch_video))
            .performScrollTo().performClick()
        await("player activity") {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it is PlayerActivity }
        }
        onMain {
            assertEquals(
                1,
                player.mediaItemCount,
            )
            assertEquals(
                Uri.fromFile(media).toString(),
                player.currentMediaItem?.mediaId,
            )
            assertEquals(
                30_000L,
                player.currentPosition,
            )
            assertFalse(player.playWhenReady)
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<PlayerActivity>().forEach {
                    it.onBackPressedDispatcher.onBackPressed()
                }
        }
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
        onMain {
            assertFalse(player.playWhenReady)
            assertEquals(
                30_000L,
                player.currentPosition,
            )
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.collapse_player),
        ).performClick()
        waitForMiniPlayer()
    }

    @Test
    fun closingBarClearsQueueAndSavesPosition() {
        onMain {
            player.pause()
            player.seekTo(42_000)
        }
        await("paused seek") { player.currentPosition == 42_000L }
        expand()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_player_session),
        ).performScrollTo().performClick()
        await("cleared queue") { player.currentMediaItem == null }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isEmpty()
        }
        val repository = GlobalContext.get().get<MediaRepository>()
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var saved: Long? = null
        while (SystemClock.elapsedRealtime() < deadline && saved != 42_000L) {
            saved =
                runBlocking { repository.getVideoState(Uri.fromFile(media).toString())?.position }
            if (saved != 42_000L) SystemClock.sleep(50)
        }
        assertEquals(
            42_000L,
            saved,
        )
    }

    @Test
    fun completedPlaybackRemovesBar() {
        onMain { player.seekTo(player.duration - 150) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun swipeDismissalUndoRestoresPlayingAndPausedSessions() {
        listOf(
            false,
            true,
        ).forEach { playing ->
            onMain {
                player.seekTo(24_000)
                player.playWhenReady = playing
            }
            await("set playing intent") { player.playWhenReady == playing }
            waitForMiniPlayer()
            composeRule.onNodeWithTag("mini_player").performTouchInput { swipeLeft() }
            await("dismissed queue") { player.currentMediaItem == null }
            composeRule.onNodeWithText(context.getString(R.string.undo)).performClick()
            await("restored queue") {
                player.mediaItemCount == 1 && player.playWhenReady == playing
            }
            onMain { assertTrue(player.currentPosition in 24_000..26_000) }
        }
    }

    @Test
    fun nestedSettingsAndNotificationReentryKeepTheSession() {
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings),
        ).performClick()
        waitForMiniPlayer()
        onMain { player.sessionActivity!!.send() }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("expanded_player").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
        onMain { assertTrue(player.isPlaying) }
    }

    @Test
    fun vaultPinExcludesPlaybackWithoutStoppingIt() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.vault)).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isEmpty()
        }
        onMain { assertTrue(player.isPlaying) }
    }

    @Test
    fun playlistCreationCoversNavigationAndKeepsPlayingWithKeyboard() {
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.playlists),
        ).performClick()
        composeRule.onNodeWithTag("top_level_fab").performClick()
        composeRule.onNodeWithText(context.getString(R.string.create_local_playlist)).performClick()
        composeRule.onNodeWithTag("playlist_creation_form").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.playlist_name),
        ).performTextInput("Playback demo")
        composeRule.onNodeWithText(context.getString(R.string.create)).assertIsDisplayed()
        onMain { assertTrue(player.isPlaying) }
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        waitForMiniPlayer()
    }

    @Test
    fun notificationFromDirectFullscreenKeepsPlayingWithBackgroundPreferenceOff() {
        onMain {
            composeRule.activity.startActivity(
                Intent(
                    context,
                    PlayerActivity::class.java,
                )
                    .setData(Uri.fromFile(media)),
            )
        }
        await("direct fullscreen playback") {
            player.isPlaying && ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).any { it is PlayerActivity }
        }
        onMain { player.sessionActivity!!.send() }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("expanded_player").fetchSemanticsNodes().isNotEmpty()
        }
        onMain { assertTrue(player.isPlaying) }
    }

    private fun expand() = composeRule.onNodeWithTag("mini_player")
        .performSemanticsAction(SemanticsActions.OnClick) { it() }

    private fun waitForMiniPlayer() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
