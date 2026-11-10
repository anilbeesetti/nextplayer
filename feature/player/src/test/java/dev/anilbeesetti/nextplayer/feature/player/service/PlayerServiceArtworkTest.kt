package dev.anilbeesetti.nextplayer.feature.player.service

import android.graphics.Bitmap
import android.media.MediaMetadata
import androidx.core.net.toUri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.core.common.di.DiQualifiers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NetworkSessions
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NextDataSourceFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerServiceArtworkTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<CoroutineScope>(named(DiQualifiers.APPLICATION_SCOPE)) { scope }
                    single<PreferencesRepository> { FakePreferencesRepository() }
                    single {
                        NextDataSourceFactory(
                            RuntimeEnvironment.getApplication(),
                            sessions = NetworkSessions(
                                resolver = NetworkConnectionResolver { null },
                                clientFactory = NetworkClientFactory {
                                    error("No network playback")
                                },
                            ),
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        stopKoin()
    }

    @Test
    fun decodedArtworkSurvivesRomMetadataScalingAndReuse() = withBitmapLoader { loader ->
        val data = artworkData()
        val bitmap = loader.decodeBitmap(data).get(
            5,
            TimeUnit.SECONDS,
        )
        publishRomMetadata(bitmap)
        publishRomMetadata(
            loader.decodeBitmap(data).get(
                5,
                TimeUnit.SECONDS,
            ),
        )
        assertFalse(bitmap.isRecycled)
        assertEquals(
            500,
            bitmap.width,
        )
        assertEquals(
            281,
            bitmap.height,
        )
    }

    @Test
    fun artworkUriSurvivesRomMetadataScalingAndReuse() = withBitmapLoader { loader ->
        val file = File.createTempFile(
            "artwork",
            ".png",
        )
        try {
            file.writeBytes(artworkData())
            val bitmap = loader.loadBitmap(file.toURI().toString().toUri()).get(
                5,
                TimeUnit.SECONDS,
            )
            publishRomMetadata(bitmap)
            publishRomMetadata(bitmap)
            assertFalse(bitmap.isRecycled)
            assertEquals(
                500,
                bitmap.width,
            )
            assertEquals(
                281,
                bitmap.height,
            )
        } finally {
            file.delete()
        }
    }

    private fun artworkData(): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(
            512,
            288,
            Bitmap.Config.ARGB_8888,
        )
            .compress(
                Bitmap.CompressFormat.PNG,
                100,
                output,
            )
        output.toByteArray()
    }

    private fun withBitmapLoader(block: (BitmapLoader) -> Unit) {
        val controller = Robolectric.buildService(PlayerService::class.java).create()
        val service = controller.get()
        val session = PlayerService::class.java.getDeclaredField("mediaSession")
            .apply { isAccessible = true }.get(service) as MediaSession
        try {
            block(session.bitmapLoader)
        } finally {
            controller.destroy()
        }
    }

    private fun publishRomMetadata(bitmap: Bitmap) {
        val metadata = MediaMetadata.Builder()
            .putBitmap(
                MediaMetadata.METADATA_KEY_DISPLAY_ICON,
                bitmap,
            )
            .putBitmap(
                MediaMetadata.METADATA_KEY_ALBUM_ART,
                bitmap,
            )
            .build()
        // RisingOS Android 15 recycles the source while resizing each metadata key to 500px.
        // Media3 puts the same bitmap in both keys, so resizing the second key crashes.
        for (key in metadata.keySet()) {
            val source = metadata.getBitmap(key) ?: continue
            if (source.width > 500 || source.height > 500) {
                val scale = 500f / maxOf(
                    source.width,
                    source.height,
                )
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt(),
                    (source.height * scale).toInt(),
                    true,
                )
                source.recycle()
            }
        }
    }
}
