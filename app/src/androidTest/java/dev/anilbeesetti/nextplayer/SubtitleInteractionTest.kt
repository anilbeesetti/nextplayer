package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.doOnPreDraw
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class SubtitleInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences get() = GlobalContext.get().get<PreferencesRepository>()

    @Test
    fun authoredLayoutsCannotBeDragged() = verifySubtitles(
        listOf(
            "alignment.mkv" to true,
            "legacy.ssa.mkv" to true,
            "bitmap.sup.mkv" to true,
            "alignment.mkv" to false,
        ),
    )

    @Test
    fun ordinaryTextCanBeDraggedAndSaved() = verifySubtitles(listOf("basic.srt.mkv" to true))

    private fun verifySubtitles(cases: List<Pair<String, Boolean>>) {
        val original = preferences.playerPreferences.value
        val directory = File(
            context.cacheDir,
            "subtitle-drag-${System.nanoTime()}",
        ).apply {
            mkdirs()
        }
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
            ).buildAsync()
        }
        val player = future.get(
            10,
            TimeUnit.SECONDS,
        )
        try {
            for ((file, styles) in cases) {
                val initialOffset = if (file == "basic.srt.mkv") 0f else -0.12f
                runBlocking {
                    preferences.updatePlayerPreferences {
                        it.copy(
                            subtitleOffsetY = initialOffset,
                            applyEmbeddedStyles = styles,
                            autoPip = false,
                            controllerAutoHideTimeout = 0,
                            autoBackgroundPlay = false,
                        )
                    }
                }
                val media = File(
                    directory,
                    "$styles-$file",
                )
                instrumentation.context.assets.open("subtitles/$file").use { input ->
                    media.outputStream().use { input.copyTo(it) }
                }
                val intent = Intent(
                    context,
                    PlayerActivity::class.java,
                ).setData(Uri.fromFile(media))
                    .putExtra(
                        "position",
                        2_000,
                    )
                ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
                    await("$file ready") {
                        main {
                            player.currentMediaItem?.mediaId == Uri.fromFile(media).toString() &&
                                player.playbackState == Player.STATE_READY &&
                                player.currentCues.cues.isNotEmpty() &&
                                player.currentCues.cues.all {
                                    (it.bitmap == null) ==
                                        (file == "basic.srt.mkv")
                                }
                        }
                    }
                    main { player.pause() }
                    await("$file paused") { main { !player.playWhenReady && !player.isPlaying } }
                    var decor: View? = null
                    scenario.onActivity { decor = it.window.decorView }
                    await("$file window focused") { main { checkNotNull(decor).hasWindowFocus() } }
                    await("$file ($styles) transport controls visible") {
                        main {
                            controlsPresent(checkNotNull(decor)) ==
                                true
                        }
                    }
                    var subtitle: SubtitleView? = null
                    scenario.onActivity { subtitle = findSubtitleView(it.window.decorView) }
                    await("$file landscape layout") {
                        main {
                            checkNotNull(decor).width >
                                checkNotNull(decor).height
                        }
                    }
                    await("$file painted") {
                        main { runCatching { paintedCenter(checkNotNull(subtitle)) }.isSuccess }
                    }
                    awaitSubtitleDraw(checkNotNull(subtitle))
                    // Hide transport controls so their buttons do not intercept a subtitle swipe.
                    for (attempt in 0 until 2) {
                        val present = checkNotNull(main { controlsPresent(checkNotNull(decor)) })
                        if (!present) break
                        assertTrue(
                            "Player window must retain focus",
                            main {
                                checkNotNull(decor).hasWindowFocus()
                            },
                        )
                        if (attempt >
                            0
                        ) {
                            android.util.Log.w(
                                "SubtitleQA",
                                "Retrying dropped setup tap for $file ($styles)",
                            )
                        }
                        onView(isRoot()).perform(
                            GeneralClickAction(
                                Tap.SINGLE,
                                { view ->
                                    val origin = IntArray(2)
                                    view.getLocationOnScreen(origin)
                                    floatArrayOf(
                                        origin[0] + view.width * 0.25f,
                                        origin[1] + view.height * 0.25f,
                                    )
                                },
                                Press.FINGER,
                            ),
                        )
                        val deadline = SystemClock.uptimeMillis() + 4000
                        while (SystemClock.uptimeMillis() < deadline &&
                            main { controlsPresent(checkNotNull(decor)) == true }
                        ) {
                            SystemClock.sleep(50)
                        }
                    }
                    await("$file ($styles) transport controls hidden") {
                        main {
                            controlsPresent(checkNotNull(decor)) ==
                                false
                        }
                    }
                    awaitSubtitleDraw(checkNotNull(subtitle))
                    var start = FloatArray(2)
                    onView(isRoot()).perform(
                        GeneralSwipeAction(
                            Swipe.SLOW,
                            {
                                paintedCenter(checkNotNull(subtitle)).also {
                                    start = it
                                }
                            },
                            {
                                floatArrayOf(
                                    start[0],
                                    start[1] - 100f,
                                )
                            },
                            Press.FINGER,
                        ),
                    )
                    instrumentation.waitForIdleSync()
                    assertTrue(
                        "Playback must stay paused during the swipe",
                        main {
                            !player.playWhenReady &&
                                !player.isPlaying
                        },
                    )
                    assertTrue(
                        "The subtitle must remain present",
                        main {
                            player.currentCues.cues.isNotEmpty()
                        },
                    )
                    if (file == "basic.srt.mkv") {
                        await("SRT position saved") {
                            preferences.playerPreferences.value.subtitleOffsetY != initialOffset
                        }
                    } else {
                        assertEquals(
                            "$file must preserve the saved text offset",
                            -0.12f,
                            preferences.playerPreferences.value.subtitleOffsetY,
                            0f,
                        )
                    }
                }
                main {
                    player.stop()
                    player.clearMediaItems()
                }
            }
        } finally {
            main {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            context.stopService(
                Intent(
                    context,
                    PlayerService::class.java,
                ),
            )
            runBlocking { preferences.updatePlayerPreferences { original } }
            directory.deleteRecursively()
        }
    }

    private fun awaitSubtitleDraw(view: View) {
        val drawn = CountDownLatch(1)
        main {
            view.doOnPreDraw { drawn.countDown() }
            view.invalidate()
        }
        assertTrue(
            "Subtitle bounds must be drawn before swiping",
            drawn.await(
                5,
                TimeUnit.SECONDS,
            ),
        )
    }

    private fun controlsPresent(view: View): Boolean? {
        fun findRoot(view: View): ViewRootForTest? {
            if (view is ViewRootForTest) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    findRoot(view.getChildAt(i))?.let { return it }
                }
            }
            return null
        }
        val root = findRoot(view) ?: return null
        val description = context.getString(coreUiR.string.select_decoders)
        return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).any {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) ==
                true
        }
    }

    private fun findSubtitleView(view: View): SubtitleView? {
        if (view is SubtitleView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findSubtitleView(
                    view.getChildAt(i),
                )?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun paintedCenter(view: SubtitleView): FloatArray {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        var left = bitmap.width
        var top = bitmap.height
        var right = 0
        var bottom = 0
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                if (bitmap.getPixel(
                        x,
                        y,
                    ) ushr 24 < 16
                ) {
                    continue
                }
                left = minOf(
                    left,
                    x,
                )
                right = maxOf(
                    right,
                    x,
                )
                top = minOf(
                    top,
                    y,
                )
                bottom = maxOf(
                    bottom,
                    y,
                )
            }
        }
        bitmap.recycle()
        assertTrue(
            "subtitle must paint pixels",
            right > left && bottom > top,
        )
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return floatArrayOf(
            location[0] + (left + right) / 2f,
            location[1] + (top + bottom) / 2f,
        )
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out: $description")
    }

    private fun <T> main(block: () -> T): T {
        val task = FutureTask(block)
        instrumentation.runOnMainSync(task)
        return task.get()
    }
}
