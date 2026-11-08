package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.state.CuesState
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the application's real source factories and renderer selection, without a custom load control. */
@RunWith(AndroidJUnit4::class)
class FfmpegSubtitlePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var activePlayer: MediaController? = null

    @Test
    fun nativeAndTextTracksRenderSeekAndReselectThroughPlayerService() {
        val directory = File(
            context.cacheDir,
            "ffmpeg-subtitle-test-${System.nanoTime()}",
        ).apply { mkdirs() }
        instrumentation.context.assets.list("subtitles")!!.forEach { name ->
            instrumentation.context.assets.open("subtitles/$name").use { input ->
                File(
                    directory,
                    name,
                ).outputStream().use { input.copyTo(it) }
            }
        }
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("X-Subtitle-Test") !=
                    "authorized"
                ) {
                    return MockResponse().setResponseCode(403)
                }
                return MockResponse().setBody(
                    Buffer().write(
                        File(
                            directory,
                            "styled.ass",
                        ).readBytes(),
                    ),
                )
            }
        }
        server.start()
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
        activePlayer = player
        val scope = CoroutineScope(Dispatchers.Main)
        val cues = main { CuesState(player).also { scope.launch { it.observe() } } }
        try {
            val cases = listOf(
                Triple(
                    "styled.ass.mkv",
                    null,
                    true,
                ),
                Triple(
                    "legacy.ssa.mkv",
                    null,
                    true,
                ),
                Triple(
                    "font.ass.mkv",
                    null,
                    true,
                ),
                Triple(
                    "bitmap.sup.mkv",
                    null,
                    true,
                ),
                Triple(
                    "vobsub.mkv",
                    null,
                    true,
                ),
                Triple(
                    "dvb.ts",
                    null,
                    true,
                ),
                Triple(
                    "styled.ass",
                    MimeTypes.TEXT_SSA,
                    true,
                ),
                Triple(
                    "legacy.ssa",
                    MimeTypes.TEXT_SSA,
                    true,
                ),
                Triple(
                    "font-embedded.ass",
                    MimeTypes.TEXT_SSA,
                    true,
                ),
                Triple(
                    "basic.srt.mkv",
                    null,
                    false,
                ),
                Triple(
                    "styled.vtt.webm",
                    null,
                    false,
                ),
                Triple(
                    "captions.vtt.webm",
                    null,
                    false,
                ),
                Triple(
                    "tx3g.mp4",
                    null,
                    false,
                ),
                Triple(
                    "basic.srt",
                    MimeTypes.APPLICATION_SUBRIP,
                    false,
                ),
                Triple(
                    "styled.vtt",
                    MimeTypes.TEXT_VTT,
                    false,
                ),
                Triple(
                    "basic.ttml",
                    MimeTypes.APPLICATION_TTML,
                    false,
                ),
            )
            for ((file, mime, bitmap) in cases + Triple(
                "http-styled.ass",
                MimeTypes.TEXT_SSA,
                true,
            )) {
                val video = if (mime ==
                    null
                ) {
                    File(
                        directory,
                        file,
                    )
                } else {
                    File(
                        directory,
                        "$file-video.mp4",
                    ).apply {
                        writeBytes(
                            File(
                                directory,
                                "video.mp4",
                            ).readBytes(),
                        )
                    }
                }
                val mediaUri = Uri.fromFile(video)
                val item = MediaItem.Builder().setUri(mediaUri).setMediaId(mediaUri.toString())
                if (mime != null) {
                    item.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(
                                if (file.startsWith(
                                        "http-",
                                    )
                                ) {
                                    Uri.parse(
                                        server.url("/styled.ass").toString(),
                                    )
                                } else {
                                    Uri.fromFile(
                                        File(
                                            directory,
                                            file,
                                        ),
                                    )
                                },
                            )
                                .setMimeType(
                                    mime,
                                ).setLanguage(
                                    "en",
                                ).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(),
                        ),
                    )
                }
                if (file.startsWith("http-")) {
                    item.setRequestMetadata(
                        MediaItem.RequestMetadata.Builder().setExtras(
                            Bundle().apply {
                                putBundle(
                                    "dev.anilbeesetti.nextplayer.api.HEADERS",
                                    Bundle().apply {
                                        putString(
                                            "X-Subtitle-Test",
                                            "authorized",
                                        )
                                    },
                                )
                            },
                        ).build(),
                    )
                }
                main {
                    player.setMediaItem(
                        item.build(),
                        2_000,
                    )
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(
                            C.TRACK_TYPE_TEXT,
                            false,
                        ).setPreferredTextLanguage("en").build()
                    player.setPlaybackSpeed(1f)
                    player.prepare()
                    player.play()
                }
                await("$file renders") {
                    assertNull(
                        "$file: ${player.playerError}",
                        player.playerError,
                    )
                    player.playbackState == Player.STATE_READY &&
                        player.currentCues.cues.any { (it.bitmap != null) == bitmap }
                }
                main {
                    player.pause()
                    assertEquals(
                        "$file movement",
                        !bitmap,
                        cues.canReposition,
                    )
                    player.seekTo(player.currentPosition + 100)
                }
                await("$file seek") {
                    player.playbackState == Player.STATE_READY &&
                        player.currentCues.cues.isNotEmpty()
                }
                val isAss = main {
                    player.currentTracks.groups.any {
                        it.type == C.TRACK_TYPE_TEXT && it.isSelected &&
                            it.getTrackFormat(0).sampleMimeType == MimeTypes.TEXT_SSA
                    }
                }
                if (isAss) {
                    main { player.seekTo(7_500) }
                    await("$file cue gap") { player.currentCues.cues.isEmpty() }
                    main {
                        assertFalse(
                            "ASS stays locked during cue gaps",
                            cues.canReposition,
                        )
                    }
                }
                main {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(
                            C.TRACK_TYPE_TEXT,
                            true,
                        ).build()
                }
                await("$file disabled") { player.currentCues.cues.isEmpty() }
                main {
                    player.switchTrack(
                        C.TRACK_TYPE_TEXT,
                        0,
                    )
                    player.seekTo(0)
                    player.play()
                }
                await("$file reselected after backwards seek") {
                    player.currentCues.cues.isNotEmpty()
                }
                main { player.pause() }
                println("PASS $file: render, seek, disable, reselect, movement eligibility")
            }
        } finally {
            main {
                scope.cancel()
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
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (main(condition)) return
            SystemClock.sleep(50)
        }
        val status = main {
            val player = activePlayer
            val tracks = player?.currentTracks?.groups?.map { group ->
                val format = group.mediaTrackGroup.getFormat(0)
                "${group.type}/${group.isSelected}/${format.sampleMimeType}"
            }
            "state=${player?.playbackState}, position=${player?.currentPosition}, tracks=$tracks"
        }
        throw AssertionError("Timed out: $description ($status)")
    }

    private fun <T> main(block: () -> T): T {
        val task = FutureTask(block)
        instrumentation.runOnMainSync(task)
        return task.get()
    }
}
