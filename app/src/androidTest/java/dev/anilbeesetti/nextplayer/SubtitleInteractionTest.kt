package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import java.io.File
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
    fun authoredLayoutsCannotBeDraggedAndTextRemainsMovable() {
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
            for ((file, styles) in listOf(
                "alignment.mkv" to true,
                "legacy.ssa.mkv" to true,
                "bitmap.sup.mkv" to true,
                "alignment.mkv" to false,
                "basic.srt.mkv" to true,
            )) {
                val initialOffset = if (file == "basic.srt.mkv") 0f else -0.12f
                runBlocking {
                    preferences.updatePlayerPreferences {
                        it.copy(
                            subtitleOffsetY = initialOffset,
                            applyEmbeddedStyles = styles,
                            autoPip = false,
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
                            player.playbackState == Player.STATE_READY &&
                                player.currentCues.cues.isNotEmpty()
                        }
                    }
                    main { player.pause() }
                    var subtitle: SubtitleView? = null
                    scenario.onActivity { subtitle = findSubtitleView(it.window.decorView) }
                    val start = main { paintedCenter(checkNotNull(subtitle)) }
                    onView(isRoot()).perform(
                        GeneralSwipeAction(
                            Swipe.SLOW,
                            { start },
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
