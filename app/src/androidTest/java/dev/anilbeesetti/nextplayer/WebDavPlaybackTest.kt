package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class WebDavPlaybackTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun directVideoPlaysWhenRootListingIsForbidden() {
        val context = instrumentation.targetContext
        val preferences = GlobalContext.get().get<PreferencesRepository>()
        val original = preferences.playerPreferences.value
        runBlocking {
            preferences.updatePlayerPreferences {
                it.copy(
                    loopMode = LoopMode.ONE,
                    resume = Resume.NO,
                    autoPip = false,
                )
            }
        }
        val video = instrumentation.context.assets.open("thumbnail-test.mp4").use { it.readBytes() }
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        try {
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requests.add(request)
                        if (request.method == "HEAD") return MockResponse().setResponseCode(200)
                        if (request.method == "PROPFIND") return MockResponse().setResponseCode(403)
                        if (request.getHeader("Authorization") !=
                            Credentials.basic(
                                "viewer",
                                "secret",
                            )
                        ) {
                            return MockResponse().setResponseCode(401)
                                .addHeader(
                                    "WWW-Authenticate",
                                    "Basic realm=\"Playback\"",
                                )
                        }
                        if (request.method != "GET" || request.path != "/Media/clip.mp4") {
                            return MockResponse().setResponseCode(404)
                        }
                        val range = request.getHeader("Range")
                        val offset = range?.removePrefix("bytes=")
                            ?.substringBefore('-')?.toInt() ?: 0
                        if (offset !in video.indices) return MockResponse().setResponseCode(416)
                        return MockResponse().setResponseCode(if (range == null) 200 else 206)
                            .addHeader(
                                "Content-Type",
                                "video/mp4",
                            )
                            .addHeader(
                                "Accept-Ranges",
                                "bytes",
                            )
                            .apply {
                                if (range != null) {
                                    addHeader(
                                        "Content-Range",
                                        "bytes $offset-${video.lastIndex}/${video.size}",
                                    )
                                }
                            }
                            .setBody(
                                Buffer().write(
                                    video,
                                    offset,
                                    video.size - offset,
                                ),
                            )
                    }
                }
                server.start()
                val authority = "viewer:secret@127.0.0.1:${server.port}"
                val uri = Uri.parse("webdav://$authority/Media/clip.mp4")
                var activity: PlayerActivity? = null
                onMain {
                    context.startActivity(
                        Intent(
                            context,
                            PlayerActivity::class.java,
                        )
                            .setData(uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                await("player activity") {
                    activity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<PlayerActivity>().firstOrNull()
                    activity != null
                }
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
                val player = future.get(
                    30,
                    TimeUnit.SECONDS,
                )
                try {
                    await("direct WebDAV video playback") {
                        assertTrue(
                            "Playback error: ${player.playerError}",
                            player.playerError == null,
                        )
                        player.isPlaying && player.currentPosition >= 200 &&
                            player.videoSize.width > 0
                    }
                    assertTrue(
                        requests.any { it.method == "GET" && it.path == "/Media/clip.mp4" },
                    )
                    assertTrue(requests.none { it.path == "/" })
                } finally {
                    onMain { activity?.finish() }
                    await("player activity cleanup") { activity?.isDestroyed != false }
                    onMain {
                        player.stop()
                        player.clearMediaItems()
                        player.release()
                    }
                }
            }
        } finally {
            context.stopService(
                Intent(
                    context,
                    PlayerService::class.java,
                ),
            )
            runBlocking { preferences.updatePlayerPreferences { original } }
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            onMain { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
