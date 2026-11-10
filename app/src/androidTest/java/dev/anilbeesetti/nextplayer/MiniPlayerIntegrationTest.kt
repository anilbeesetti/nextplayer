package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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
    private lateinit var originalApplicationPreferences: ApplicationPreferences
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val preferences get() = GlobalContext.get().get<PreferencesRepository>()

    @Before
    fun startPlayback() {
        originalPreferences = preferences.playerPreferences.value
        originalApplicationPreferences = preferences.applicationPreferences.value
        originalOrientation = composeRule.activity.requestedOrientation
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
        if (::originalApplicationPreferences.isInitialized) {
            runBlocking {
                preferences.updateApplicationPreferences { originalApplicationPreferences }
            }
        }
        onMain { composeRule.activity.requestedOrientation = originalOrientation }
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

    @Test
    fun expandedLayoutsKeepEveryActionVisibleAcrossRotationAndThemes() {
        onMain {
            val uri = Uri.fromFile(media)
            player.setMediaItems(
                (0..2).map { index ->
                    MediaItem.Builder().setMediaId("$uri#$index").setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder().setTitle("Playback design check.mp4")
                                .setSubtitle("Test media").build(),
                        ).build()
                },
                1,
                30_000,
            )
            player.prepare()
            player.pause()
        }
        await("visual test queue") { player.playbackState == Player.STATE_READY }
        expand()
        for (dark in listOf(
            false,
            true,
        )) {
            runBlocking {
                preferences.updateApplicationPreferences {
                    it.copy(themeConfig = if (dark) ThemeConfig.ON else ThemeConfig.OFF)
                }
            }
            for (landscape in listOf(
                false,
                true,
            )) {
                onMain {
                    composeRule.activity.requestedOrientation = if (landscape) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                composeRule.waitUntil(10_000) {
                    val expected = if (landscape) {
                        Configuration.ORIENTATION_LANDSCAPE
                    } else {
                        Configuration.ORIENTATION_PORTRAIT
                    }
                    composeRule.activity.resources.configuration.orientation == expected
                }
                for (playing in listOf(
                    false,
                    true,
                )) {
                    onMain {
                        player.pause()
                        player.seekTo(30_000)
                        if (playing) player.play()
                    }
                    await("visual playback state") { player.isPlaying == playing }
                    composeRule.waitForIdle()
                    composeRule.onNodeWithContentDescription(
                        context.getString(R.string.playback_output),
                    )
                        .assertIsDisplayed()
                    composeRule.onNodeWithContentDescription(
                        context.getString(R.string.stop_player_session),
                    )
                        .assertIsDisplayed()
                    composeRule.onNodeWithText(
                        context.getString(R.string.watch_video),
                    ).assertIsDisplayed()
                    if (landscape) {
                        composeRule.onNodeWithTag("playback_header").assertDoesNotExist()
                    } else {
                        composeRule.onNodeWithTag("playback_header").assertIsDisplayed()
                    }
                    val theme = if (dark) "dark" else "light"
                    val orientation = if (landscape) "landscape" else "portrait"
                    val state = if (playing) "playing" else "paused"
                    capturePlayback("$theme-$orientation-$state")
                }
                val progress = composeRule.onNodeWithContentDescription(
                    context.getString(R.string.player_controls_progress),
                )
                progress.performTouchInput {
                    down(center)
                    moveBy(
                        Offset(
                            width * .2f,
                            0f,
                        ),
                        delayMillis = 300,
                    )
                }
                try {
                    val theme = if (dark) "dark" else "light"
                    val orientation = if (landscape) "landscape" else "portrait"
                    capturePlayback("$theme-$orientation-scrubbing")
                } finally {
                    progress.performTouchInput { up() }
                }
                await("committed visual seek") { player.currentPosition > 40_000 }
                composeRule.onNodeWithTag("expanded_player").assertIsDisplayed()
            }
        }
        onMain {
            player.pause()
            player.seekTo(
                1,
                30_000,
            )
        }
        await("paused endpoint capture") {
            !player.playWhenReady &&
                player.currentPosition == 30_000L
        }
        val progress = composeRule.onNodeWithContentDescription(
            context.getString(R.string.player_controls_progress),
        )
        progress.assertIsDisplayed()
        // Android touch coordinates can quantize the endpoint by one physical pixel.
        val tolerance = 1.5f / progress.fetchSemanticsNode().boundsInRoot.width
        progress.performTouchInput {
            down(center)
            moveTo(
                Offset(
                    -20f,
                    centerY,
                ),
                delayMillis = 300,
            )
        }
        try {
            val start = progress.fetchSemanticsNode().config
            assertEquals(
                0f,
                start[SemanticsProperties.ProgressBarRangeInfo].current,
                tolerance,
            )
            capturePlayback("dark-landscape-seek-start")
            progress.performTouchInput {
                moveTo(
                    Offset(
                        width + 200f,
                        centerY,
                    ),
                    delayMillis = 300,
                )
            }
            val end = progress.fetchSemanticsNode().config
            assertEquals(
                1f,
                end[SemanticsProperties.ProgressBarRangeInfo].current,
                tolerance,
            )
            capturePlayback("dark-landscape-seek-end")
        } finally {
            progress.performTouchInput { up() }
        }
    }

    @Test
    fun overflowingLandscapeTitleActuallyMovesWhilePlayingAndEllipsizesWhenPaused() {
        onMain {
            player.pause()
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        }
        expand()
        val clock = composeRule.mainClock
        val originalAutoAdvance = clock.autoAdvance
        try {
            clock.autoAdvance = false
            onMain { player.play() }
            await("marquee playback") { player.isPlaying }
            val title = composeRule.onNodeWithTag("playback_media_title")
            composeRule.waitUntil(10_000) {
                clock.advanceTimeByFrame()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                layouts.singleOrNull()?.isLineEllipsized(0) == false
            }
            clock.advanceTimeBy(400)
            val before = title.captureToImage().asAndroidBitmap()
            capturePlayback("landscape-title-scroll-start")
            clock.advanceTimeBy(2400)
            val after = title.captureToImage().asAndroidBitmap()
            capturePlayback("landscape-title-scroll-end")
            assertFalse(
                "Overflowing playing title must scroll",
                before.sameAs(after),
            )
            onMain { player.pause() }
            await("paused marquee") { !player.playWhenReady }
            clock.advanceTimeBy(100)
            composeRule.waitUntil(10_000) {
                clock.advanceTimeByFrame()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                layouts.singleOrNull()?.isLineEllipsized(0) == true
            }
            capturePlayback("landscape-title-paused")
        } finally {
            clock.autoAdvance = originalAutoAdvance
        }
    }

    private fun capturePlayback(name: String) {
        if (InstrumentationRegistry.getArguments().getString("playbackScreenshots") !=
            "true"
        ) {
            return
        }
        val root = composeRule.onNodeWithTag("expanded_player")
        val bounds = root.fetchSemanticsNode().boundsInRoot
        val bitmap = root.captureToImage().asAndroidBitmap()
        val directory = File(
            context.getExternalFilesDir(null),
            "playback-parity",
        ).apply {
            mkdirs()
        }
        File(
            directory,
            "$name.png",
        ).outputStream().use {
            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                it,
            )
        }
        val measurements = JSONObject().put(
            "width",
            bitmap.width,
        ).put(
            "height",
            bitmap.height,
        )
        for (tag in listOf(
            "playback_title",
            "playback_artwork",
            "playback_seek_track",
            "playback_position",
            "playback_output_action",
            "playback_queue_action",
            "playback_transport",
            "playback_modes",
        )) {
            val rect = composeRule.onNodeWithTag(
                tag,
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            measurements.put(
                tag,
                JSONArray(
                    listOf(
                        rect.left - bounds.left,
                        rect.top - bounds.top,
                        rect.right - bounds.left,
                        rect.bottom - bounds.top,
                    ),
                ),
            )
        }
        File(
            directory,
            "$name.json",
        ).writeText(measurements.toString())
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
