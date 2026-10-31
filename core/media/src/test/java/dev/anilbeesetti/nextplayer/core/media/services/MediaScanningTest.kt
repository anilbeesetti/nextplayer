package dev.anilbeesetti.nextplayer.core.media.services

import android.content.Context
import android.media.MediaScannerConnection
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [24, 28, 29, 30],
    manifest = Config.NONE,
    shadows = [MediaScanningTest.ScannerShadow::class],
)
class MediaScanningTest {
    @Test
    fun `batch scan completes without mismatched mime types`() = runBlocking {
        withTimeout(timeMillis = 1_000) {
            assertTrue(
                RuntimeEnvironment.getApplication().scanPaths(
                    paths = listOf(
                        "/Movies/one.mp4",
                        "/Movies/two.mkv",
                    ),
                ),
            )
        }
    }

    @Implements(MediaScannerConnection::class)
    class ScannerShadow {
        companion object {
            @JvmStatic
            @Implementation
            fun scanFile(
                context: Context,
                paths: Array<String>,
                mimeTypes: Array<String>?,
                callback: MediaScannerConnection.OnScanCompletedListener,
            ) {
                // Older Android indexes MIME types once per path before reporting completion.
                for ((index, path) in paths.withIndex()) {
                    mimeTypes?.get(index)
                    callback.onScanCompleted(
                        path,
                        null,
                    )
                }
            }
        }
    }
}
