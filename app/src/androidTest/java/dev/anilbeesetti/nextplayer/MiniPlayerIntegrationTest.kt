package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
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
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.PlaybackOverlay
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
    private val additionalMedia = mutableListOf<File>()
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
        additionalMedia.forEach { it.delete() }
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
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.waitForIdle()
            await("dismissed queue") { player.currentMediaItem == null }
            composeRule.onNodeWithText(context.getString(R.string.undo)).performClick()
            await("restored queue") {
                player.mediaItemCount == 1 && player.playWhenReady == playing
            }
            onMain { assertTrue(player.currentPosition in 24_000..26_000) }
        }
    }

    @Test
    fun nestedSettingsReachWindowEdgeAndLastPreferenceClearsMiniPlayer() {
        onMain { player.pause() }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings),
        ).performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val screen = composeRule.onNodeWithTag("settings_screen").fetchSemanticsNode().boundsInRoot
        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(
            window.bottom,
            screen.bottom,
            1f,
        )
        composeRule.onNodeWithText(context.getString(R.string.about_name)).performScrollTo()
        val last = composeRule.onNodeWithText(
            context.getString(R.string.about_name),
        ).fetchSemanticsNode().boundsInRoot
        val mini = composeRule.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Last preference must scroll above playback",
            last.bottom < mini.top,
        )
        val image = composeRule.onNodeWithTag("settings_screen").captureToImage().asAndroidBitmap()
        val background = image.getPixel(
            4,
            image.height / 2,
        )
        val bottom = image.getPixel(
            4,
            image.height - 4,
        )
        assertTrue(
            "Hidden navigation must not leave its gradient at the screen edge",
            kotlin.math.abs(Color.red(background) - Color.red(bottom)) <= 2 &&
                kotlin.math.abs(Color.green(background) - Color.green(bottom)) <= 2 &&
                kotlin.math.abs(Color.blue(background) - Color.blue(bottom)) <= 2,
        )
        captureNode(
            "settings_screen",
            "queue-settings-edge-to-edge",
        )
    }

    @Test
    fun nestedSettingsAndNotificationReentryKeepTheSession() {
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings),
        ).performClick()
        waitForMiniPlayer()
        composeRule.mainClock.autoAdvance = false
        onMain { player.sessionActivity!!.send() }
        composeRule.waitUntil(10_000) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag("expanded_player").fetchSemanticsNodes().isNotEmpty()
        }
        settlePlaybackUi()
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
        composeRule.mainClock.autoAdvance = false
        onMain { player.sessionActivity!!.send() }
        composeRule.waitUntil(10_000) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag("expanded_player").fetchSemanticsNodes().isNotEmpty()
        }
        settlePlaybackUi()
        onMain { assertTrue(player.isPlaying) }
    }

    @Test
    fun expandedLayoutsKeepEveryActionVisibleAcrossRotationAndThemes() {
        val clock = composeRule.mainClock
        val automatic = clock.autoAdvance
        clock.autoAdvance = false
        try {
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
                        settlePlaybackUi()
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
                        settlePlaybackUi()
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
                        settlePlaybackUi()
                        capturePlayback("$theme-$orientation-scrubbing")
                    } finally {
                        progress.performTouchInput { up() }
                        settlePlaybackUi()
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
            settlePlaybackUi()
            progress.assertIsDisplayed()
            // Android touch coordinates can quantize the endpoint by one physical pixel.
            val tolerance = 1.5f / progress.fetchSemanticsNode().boundsInRoot.width
            progress.performTouchInput {
                down(center)
            }
            settlePlaybackUi()
            repeat(12) { step ->
                progress.performTouchInput {
                    moveTo(
                        Offset(
                            centerX - (centerX + 100f) * (step + 1) / 12f,
                            centerY,
                        ),
                        delayMillis = 25,
                    )
                }
                clock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
            try {
                settlePlaybackUi()
                val start = progress.fetchSemanticsNode().config
                val sliderBounds = progress.fetchSemanticsNode().boundsInRoot
                val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                val positionText = composeRule.onNodeWithTag("playback_position")
                    .fetchSemanticsNode().config[SemanticsProperties.Text]
                assertEquals(
                    "Slider=$sliderBounds slop=$touchSlop time=$positionText",
                    0f,
                    start[SemanticsProperties.ProgressBarRangeInfo].current,
                    tolerance,
                )
                settlePlaybackUi()
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
                settlePlaybackUi()
                val end = progress.fetchSemanticsNode().config
                assertEquals(
                    1f,
                    end[SemanticsProperties.ProgressBarRangeInfo].current,
                    tolerance,
                )
                settlePlaybackUi()
                capturePlayback("dark-landscape-seek-end")
            } finally {
                progress.performTouchInput { up() }
            }
        } finally {
            onMain { player.pause() }
            clock.advanceTimeByFrame()
            clock.autoAdvance = automatic
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
            clock.advanceTimeBy(400)
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

    @Test
    fun shortVideosUpdateProgressBetweenSnapshotTicksIncludingDoubleSpeed() {
        onMain { player.pause() }
        val clock = composeRule.mainClock
        val automatic = clock.autoAdvance
        clock.autoAdvance = false
        try {
            for ((asset, duration) in listOf(
                "playback-red-15s.mp4" to 15_000L,
                "playback-blue-20s.mp4" to 20_000L,
            )) {
                val file = testVideo(asset)
                onMain {
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    player.prepare()
                }
                await("short video ready") { player.playbackState == Player.STATE_READY }
                if (composeRule.onAllNodesWithTag(
                        "mini_player",
                    ).fetchSemanticsNodes().isNotEmpty()
                ) {
                    expand()
                    clock.advanceTimeBy(400)
                }
                val slider = composeRule.onNodeWithContentDescription(
                    context.getString(R.string.player_controls_progress),
                )
                fun progress(): Float {
                    val config = slider.fetchSemanticsNode().config
                    return config[SemanticsProperties.ProgressBarRangeInfo].current
                }
                for (speed in listOf(
                    1f,
                    2f,
                )) {
                    onMain {
                        player.seekTo(0)
                        player.setPlaybackSpeed(speed)
                        player.play()
                    }
                    await("short video playing") { player.isPlaying }
                    val samples = mutableListOf<Float>()
                    repeat(12) {
                        SystemClock.sleep(25)
                        clock.advanceTimeByFrame()
                        samples += progress()
                    }
                    assertTrue(
                        "Progress must update within 500ms: $samples",
                        samples.distinct().size >= 6,
                    )
                    assertTrue(samples.zipWithNext().all { (a, b) -> b >= a && b - a < .08f })
                    onMain { player.pause() }
                    clock.advanceTimeBy(64)
                    val paused = progress()
                    SystemClock.sleep(100)
                    clock.advanceTimeBy(64)
                    assertEquals(
                        paused,
                        progress(),
                        .003f,
                    )
                    onMain { player.seekTo(duration / 2) }
                    await("short video seek") {
                        kotlin.math.abs(player.currentPosition - duration / 2) <
                            200
                    }
                    clock.advanceTimeBy(64)
                    assertEquals(
                        .5f,
                        progress(),
                        .02f,
                    )
                }
            }
            capturePlayback("short-video-paused-seek")
        } finally {
            onMain {
                player.pause()
                player.setPlaybackSpeed(1f)
            }
            clock.advanceTimeByFrame()
            clock.autoAdvance = automatic
        }
    }

    @Test
    fun artworkMorphAndPagingKeepRealThumbnailsAndQueueDurations() {
        val red = testVideo("playback-red-15s.mp4")
        val blue = testVideo("playback-blue-20s.mp4")
        onMain {
            player.pause()
            player.setMediaItems(
                listOf(
                    red,
                    blue,
                    red,
                ).mapIndexed { index, file ->
                    MediaItem.Builder().setMediaId(
                        Uri.fromFile(file).toString(),
                    ).setUri(Uri.fromFile(file))
                        .setMediaMetadata(
                            MediaMetadata.Builder().setTitle("Playback motion ${index + 1}")
                                .setDurationMs(if (index == 1) 20_000 else 15_000).build(),
                        ).build()
                },
            )
            player.prepare()
        }
        await("artwork video ready") { player.playbackState == Player.STATE_READY }
        fun artworkIsBlue(blueExpected: Boolean): Boolean {
            val bitmap = composeRule.onNodeWithTag(
                "playback_artwork",
            ).captureToImage().asAndroidBitmap()
            val color = bitmap.getPixel(
                bitmap.width / 2 + 10,
                bitmap.height / 2 + 10,
            )
            return if (blueExpected) {
                Color.blue(color) > Color.red(color) + 70
            } else {
                Color.red(color) >
                    Color.blue(color) + 70
            }
        }
        val clock = composeRule.mainClock
        val automatic = clock.autoAdvance
        clock.autoAdvance = false
        try {
            clock.advanceTimeByFrame()
            composeRule.waitForIdle()
            captureNode(
                "mini_player",
                "motion-compact",
            )
            expand(advanceTransition = false)
            for (step in 1..5) {
                clock.advanceTimeBy(64)
                capturePlayback("motion-expand-$step")
            }
            composeRule.waitUntil(10_000) { artworkIsBlue(false) }
            capturePlayback("motion-expanded-red")
        } finally {
            clock.autoAdvance = automatic
        }
        composeRule.onNodeWithTag("playback_artwork").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        await("paged to blue video") { player.currentMediaItemIndex == 1 }
        composeRule.waitUntil(10_000) { artworkIsBlue(true) }
        capturePlayback("motion-expanded-blue")
        composeRule.onNodeWithTag("playback_artwork").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        await("paged to duplicate red video") { player.currentMediaItemIndex == 2 }
        composeRule.waitUntil(10_000) { artworkIsBlue(false) }
        composeRule.onNodeWithTag("playback_artwork").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        await("paged back to blue video") { player.currentMediaItemIndex == 1 }
        composeRule.waitUntil(10_000) { artworkIsBlue(true) }
        composeRule.onNodeWithTag("playback_queue_action").performClick()
        composeRule.onNodeWithText(context.getString(R.string.queue_next_up)).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "queue_duration_0",
            useUnmergedTree = true,
        ).assertTextEquals("00:15")
        composeRule.onNodeWithTag(
            "queue_duration_1",
            useUnmergedTree = true,
        ).assertTextEquals("00:20")
        captureNode(
            "playback_queue",
            "motion-queue",
        )
        val move = composeRule.onNodeWithTag("queue_item_1").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first {
            it.label ==
                context.getString(R.string.queue_move_up)
        }
        composeRule.runOnIdle { assertTrue(move.action()) }
        await("queue reorder") { player.getMediaItemAt(0).mediaId == Uri.fromFile(blue).toString() }
        repeat(2) {
            composeRule.onAllNodesWithContentDescription(
                context.getString(R.string.remove),
            )[0].performClick()
            val remaining = 2 - it
            await("queue removal") { player.mediaItemCount == remaining }
        }
        composeRule.onAllNodesWithContentDescription(
            context.getString(R.string.remove),
        ).assertCountEquals(0)
    }

    @Test
    fun queueDragAndSwipeKeepStableOccurrencesAndCancellationNeverRemoves() {
        val red = testVideo("playback-red-15s.mp4")
        val blue = testVideo("playback-blue-20s.mp4")
        onMain {
            player.pause()
            player.setMediaItems(
                listOf(
                    red,
                    blue,
                    red,
                ).mapIndexed { i, file ->
                    MediaItem.Builder().setMediaId(
                        Uri.fromFile(file).toString(),
                    ).setUri(Uri.fromFile(file))
                        .setMediaMetadata(
                            MediaMetadata.Builder().setTitle("Queue video ${i + 1}")
                                .setDurationMs(if (i == 1) 20_000 else 15_000).build(),
                        ).build()
                },
            )
            player.prepare()
        }
        await("queue videos ready") { player.playbackState == Player.STATE_READY }
        waitForMiniPlayer()
        val mini = composeRule.onNodeWithTag("mini_player")
        val miniWidth = mini.fetchSemanticsNode().boundsInRoot.width
        mini.performTouchInput {
            down(
                Offset(
                    width * .9f,
                    centerY,
                ),
            )
        }
        repeat(12) {
            composeRule.onNodeWithTag("compact_swipe").performTouchInput {
                moveBy(
                    Offset(
                        -miniWidth * .06f,
                        0f,
                    ),
                    32,
                )
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        captureNode(
            "compact_swipe",
            "queue-mini-dismiss-reveal",
        )
        composeRule.onNodeWithTag("compact_swipe").performTouchInput { cancel() }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        onMain {
            assertEquals(
                3,
                player.mediaItemCount,
            )
        }
        expand()
        composeRule.onNodeWithTag("playback_queue_action").performClick()
        composeRule.waitForIdle()
        captureNode(
            "playback_queue",
            "queue-artwork-duration",
        )
        val third = composeRule.onNodeWithTag("queue_item_2").fetchSemanticsNode().boundsInRoot
        val travel = (third.height + with(composeRule.density) { 8.dp.toPx() }) * 2
        val handleStart = composeRule.onNodeWithTag(
            "queue_drag_2",
            true,
        ).fetchSemanticsNode().boundsInRoot.center.y
        composeRule.onNodeWithTag(
            "queue_drag_2",
            true,
        ).performTouchInput { down(center) }
        val dragGeometry = JSONArray()
        repeat(20) { step ->
            composeRule.onNodeWithTag("playback_queue").performTouchInput {
                moveBy(
                    Offset(
                        0f,
                        -travel / 20,
                    ),
                    32,
                )
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            var index = -1
            onMain {
                index = (0 until player.mediaItemCount).first {
                    player.getMediaItemAt(it).mediaMetadata.title.toString() == "Queue video 3"
                }
            }
            val actual = composeRule.onNodeWithTag(
                "queue_drag_$index",
                true,
            )
                .fetchSemanticsNode().boundsInRoot.center.y
            val expected = handleStart - travel * (step + 1) / 20
            assertEquals(
                "Dragged row follows the finger at step $step",
                expected,
                actual,
                with(composeRule.density) { 16.dp.toPx() },
            )
            dragGeometry.put(
                JSONObject().put(
                    "step",
                    step + 1,
                ).put(
                    "expectedY",
                    expected,
                )
                    .put(
                        "actualY",
                        actual,
                    ).put(
                        "queueIndex",
                        index,
                    ),
            )
        }
        onMain {
            assertEquals(
                3,
                player.mediaItemCount,
            )
            assertEquals(
                "Queue video 3",
                player.getMediaItemAt(0).mediaMetadata.title.toString(),
            )
        }
        val handleEnd = composeRule.onNodeWithTag(
            "queue_drag_0",
            true,
        ).fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(
            "The reordered row must stay under the finger",
            handleStart - travel,
            handleEnd,
            with(composeRule.density) { 16.dp.toPx() },
        )
        if (InstrumentationRegistry.getArguments().getString("playbackScreenshots") == "true") {
            File(
                context.getExternalFilesDir(null),
                "playback-motion/queue-reorder-geometry.json",
            )
                .writeText(dragGeometry.toString())
        }
        captureNode(
            "playback_queue",
            "queue-reordering",
        )
        composeRule.onNodeWithTag("playback_queue").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithTag("queue_item_1")
        val rowWidth = row.fetchSemanticsNode().boundsInRoot.width
        row.performTouchInput {
            down(
                Offset(
                    width * .9f,
                    centerY,
                ),
            )
        }
        repeat(12) {
            composeRule.onNodeWithTag("playback_queue").performTouchInput {
                moveBy(
                    Offset(
                        -rowWidth * .06f,
                        0f,
                    ),
                    32,
                )
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        captureNode(
            "playback_queue",
            "queue-remove-reveal",
        )
        composeRule.onNodeWithTag("playback_queue").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        await("swipe removed queued occurrence") { player.mediaItemCount == 2 }
        onMain {
            assertEquals(
                "Queue video 3",
                player.getMediaItemAt(0).mediaMetadata.title.toString(),
            )
        }
        composeRule.onNodeWithTag("queue_item_1").performTouchInput { swipeLeft() }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        await("queue keeps one video") { player.mediaItemCount == 1 }
        onMain {
            assertTrue(
                "Removing the current final row must keep the remaining queue active; state=${player.playbackState}",
                player.playbackState != Player.STATE_ENDED,
            )
        }
        composeRule.onNodeWithTag("queue_item_0").performTouchInput { swipeLeft() }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        onMain {
            assertEquals(
                1,
                player.mediaItemCount,
            )
        }
    }

    @Test
    fun compactNavigationKeepsItsNodeAndMovesWithoutAJump() {
        onMain { player.pause() }
        val mini = composeRule.onNodeWithTag("mini_player")
        val initial = mini.fetchSemanticsNode()
        val initialBounds = initial.boundsInRoot
        val clock = composeRule.mainClock
        val automatic = clock.autoAdvance
        clock.autoAdvance = false
        try {
            composeRule.onNodeWithContentDescription(
                context.getString(R.string.settings),
            ).performClick()
            clock.advanceTimeBy(64)
            val mid = mini.fetchSemanticsNode()
            val midBounds = mid.boundsInRoot
            clock.advanceTimeBy(300)
            val end = mini.fetchSemanticsNode()
            val endBounds = end.boundsInRoot
            assertEquals(
                initial.id,
                mid.id,
            )
            assertEquals(
                initial.id,
                end.id,
            )
            assertTrue(midBounds.top >= initialBounds.top)
            assertTrue(midBounds.top < endBounds.top)
            onMain { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            clock.advanceTimeBy(400)
            assertEquals(
                initial.id,
                mini.fetchSemanticsNode().id,
            )
            assertEquals(
                initialBounds.top,
                mini.fetchSemanticsNode().boundsInRoot.top,
                1f,
            )
        } finally {
            clock.autoAdvance = automatic
        }
    }

    @Test
    fun portraitRailTransitionClipsControlsInsideTheMovingSheet() {
        onMain {
            player.pause()
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_PORTRAIT
        }
        val clock = composeRule.mainClock
        val automatic = clock.autoAdvance
        clock.autoAdvance = false
        try {
            onMain {
                composeRule.activity.setContent {
                    NextPlayerTheme {
                        Box(Modifier.fillMaxSize().testTag("rail_motion_root")) {
                            Box(
                                Modifier.width(
                                    80.dp,
                                ).fillMaxHeight().background(ComposeColor.Magenta),
                            )
                            PlaybackOverlay(
                                compactBottom = 24.dp,
                                compactStart = 80.dp,
                                hidden = false,
                                expansionRequest = 0,
                                focusRequester = remember {
                                    FocusRequester()
                                },
                                onCompactHeightChanged = {},
                                onVisibilityChanged = {},
                                onExpandedChanged = {},
                            )
                        }
                    }
                }
            }
            composeRule.waitUntil(10_000) {
                clock.advanceTimeByFrame()
                composeRule.onAllNodesWithTag("rail_motion_root").fetchSemanticsNodes().isNotEmpty()
            }
            waitForMiniPlayer()
            expand(advanceTransition = false)
            clock.advanceTimeByFrame()
            composeRule.waitForIdle()
            clock.advanceTimeBy(64)
            composeRule.waitForIdle()
            clock.advanceTimeByFrame()
            val root = composeRule.onNodeWithTag("rail_motion_root")
            val sheet = composeRule.onNodeWithTag(
                "playback_sheet",
            ).fetchSemanticsNode().boundsInRoot
            val track = composeRule.onNodeWithTag(
                "playback_seek_track",
            ).fetchSemanticsNode().boundsInRoot
            val rootBounds = root.fetchSemanticsNode().boundsInRoot
            val bitmap = root.captureToImage().asAndroidBitmap()
            val x = (track.left - rootBounds.left + 4).toInt()
            val y = (track.center.y - rootBounds.top).toInt()
            assertTrue(
                "Sheet=$sheet track=$track root=$rootBounds sample=$x,$y",
                x < sheet.left - rootBounds.left - 8,
            )
            assertEquals(
                Color.MAGENTA,
                bitmap.getPixel(
                    x,
                    y,
                ),
            )
            captureNode(
                "rail_motion_root",
                "motion-portrait-rail",
            )
        } finally {
            clock.advanceTimeBy(400)
            clock.autoAdvance = automatic
        }
    }

    private fun testVideo(asset: String): File = File(
        context.cacheDir,
        asset,
    ).also { file ->
        additionalMedia += file
        instrumentation.context.assets.open(asset).use { source ->
            file.outputStream().use { source.copyTo(it) }
        }
    }

    private fun captureNode(tag: String, name: String) {
        if (InstrumentationRegistry.getArguments().getString("playbackScreenshots") !=
            "true"
        ) {
            return
        }
        val file = File(
            context.getExternalFilesDir(null),
            "playback-motion/$name.png",
        )
        file.parentFile!!.mkdirs()
        file.outputStream().use {
            composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                .compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    it,
                )
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
            if (name.startsWith("motion-") ||
                name.startsWith("short-video")
            ) {
                "playback-motion"
            } else {
                "playback-parity"
            },
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
            "transition_artwork",
            "transition_metadata",
        )) {
            if (composeRule.onAllNodesWithTag(
                    tag,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isEmpty()
            ) {
                continue
            }
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

    private fun expand(advanceTransition: Boolean = true) {
        composeRule.onNodeWithTag("mini_player")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        if (advanceTransition) settlePlaybackUi()
    }

    private fun settlePlaybackUi() {
        if (!composeRule.mainClock.autoAdvance) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.waitForIdle()
        }
    }

    private fun waitForMiniPlayer() {
        composeRule.waitUntil(10_000) {
            if (!composeRule.mainClock.autoAdvance) composeRule.mainClock.advanceTimeByFrame()
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
