package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalCastServerTest {
    private val bytes = ByteArray(256 * 1024) { (it % 251).toByte() }
    private val server = LocalCastServer(
        RuntimeEnvironment.getApplication(),
        DataSource.Factory {
            ByteArrayDataSource(bytes)
        },
    )
    private val uri = Uri.parse("content://videos/private/42")

    @After
    fun tearDown() = server.stop()

    @Test
    fun getStreamsCompleteVideoWithMimeAndLength() {
        request().useResponse {
            assertEquals(
                200,
                responseCode,
            )
            assertEquals(
                "video/mp4",
                contentType,
            )
            assertEquals(
                bytes.size.toLong(),
                contentLengthLong,
            )
            assertEquals(
                "bytes",
                getHeaderField("Accept-Ranges"),
            )
            assertEquals(
                "no-store",
                getHeaderField("Cache-Control"),
            )
            assertArrayEquals(
                bytes,
                inputStream.readBytes(),
            )
        }
    }

    @Test
    fun streamingLocksAreHeldOnlyWhileServerIsRunning() {
        assertFalse(checkNotNull(ShadowPowerManager.getLatestWakeLock()).isHeld)
        server.register(
            uri,
            "video/mp4",
            "127.0.0.1",
        )
        val lock = checkNotNull(ShadowPowerManager.getLatestWakeLock())
        val wifi = RuntimeEnvironment.getApplication().getSystemService(Context.WIFI_SERVICE)
            as WifiManager
        assertTrue(lock.isHeld)
        assertEquals(
            1,
            shadowOf(wifi).activeLockCount,
        )
        server.stop()
        assertFalse(lock.isHeld)
        assertEquals(
            0,
            shadowOf(wifi).activeLockCount,
        )
    }

    @Test
    fun headReportsLengthWithoutBody() {
        request(method = "HEAD").useResponse {
            assertEquals(
                200,
                responseCode,
            )
            assertEquals(
                bytes.size.toLong(),
                contentLengthLong,
            )
            assertEquals(
                0,
                inputStream.readBytes().size,
            )
        }
    }

    @Test
    fun seekReturnsOnlyRequestedBytes() {
        request(range = "bytes=12345-23456").useResponse {
            assertEquals(
                206,
                responseCode,
            )
            assertEquals(
                "bytes 12345-23456/${bytes.size}",
                getHeaderField("Content-Range"),
            )
            assertArrayEquals(
                bytes.copyOfRange(
                    12345,
                    23457,
                ),
                inputStream.readBytes(),
            )
        }
    }

    @Test
    fun suffixAndOpenEndedRangesWork() {
        request(range = "bytes=-17").useResponse {
            assertEquals(
                206,
                responseCode,
            )
            assertArrayEquals(
                bytes.takeLast(17).toByteArray(),
                inputStream.readBytes(),
            )
        }
        request(range = "bytes=${bytes.size - 31}-").useResponse {
            assertEquals(
                206,
                responseCode,
            )
            assertArrayEquals(
                bytes.takeLast(31).toByteArray(),
                inputStream.readBytes(),
            )
        }
    }

    @Test
    fun malformedOrUnsatisfiableRangesAreRejected() {
        listOf(
            "bytes=999999-",
            "bytes=20-10",
            "bytes=0-1,3-4",
            "bytes=-0",
            "bytes=+1-3",
            "other=0-1",
        )
            .forEach { range ->
                request(range = range).useResponse {
                    assertEquals(
                        range,
                        416,
                        responseCode,
                    )
                    assertEquals(
                        "bytes */${bytes.size}",
                        getHeaderField("Content-Range"),
                    )
                }
            }
    }

    @Test
    fun arbitraryPathsAndUrisCannotBeRead() {
        val registered = server.register(
            uri,
            "video/mp4",
            "127.0.0.1",
        )
        listOf(
            "/",
            "/etc/passwd",
            "/content%3A%2F%2Fvideos%2Fprivate%2F42",
        ).forEach { path ->
            (
                URL(
                    URL(registered),
                    path,
                ).openConnection() as HttpURLConnection
                ).useResponse {
                assertEquals(
                    404,
                    responseCode,
                )
            }
        }
    }

    @Test
    fun sameResourceKeepsTokenUntilStopAndRevokesItOnRestart() {
        val old = server.register(
            uri,
            "video/mp4",
            "127.0.0.1",
        )
        assertEquals(
            old,
            server.register(
                uri,
                "video/mp4",
                "127.0.0.1",
            ),
        )
        server.stop()
        val fresh = server.register(
            uri,
            "video/mp4",
            "127.0.0.1",
        )
        assertNotEquals(
            URL(old).path,
            URL(fresh).path,
        )
        (
            URL(
                URL(fresh),
                URL(old).path,
            ).openConnection() as HttpURLConnection
            ).useResponse {
            assertEquals(
                404,
                responseCode,
            )
        }
    }

    @Test
    fun writeMethodsAreRejected() {
        request(method = "DELETE").useResponse {
            assertEquals(
                405,
                responseCode,
            )
        }
    }

    @Test
    fun revokedReadPermissionIsNotExposedAsInternalError() {
        val denied = LocalCastServer(
            RuntimeEnvironment.getApplication(),
            DataSource.Factory {
                throw SecurityException("revoked")
            },
        )
        try {
            val url = denied.register(
                uri,
                "video/mp4",
                "127.0.0.1",
            )
            (URL(url).openConnection() as HttpURLConnection).useResponse {
                assertEquals(
                    403,
                    responseCode,
                )
            }
        } finally {
            denied.stop()
        }
    }

    @Test
    fun byteRangeHandlesBoundariesAndOverflow() {
        assertEquals(
            0L..9L,
            byteRange(
                "bytes=0-999",
                10,
            ),
        )
        assertEquals(
            0L..9L,
            byteRange(
                "bytes=-999",
                10,
            ),
        )
        assertEquals(
            9L..9L,
            byteRange(
                "bytes=9-",
                10,
            ),
        )
        listOf(
            "bytes=0-0",
            "bytes=-1",
        ).forEach {
            assertNull(
                byteRange(
                    it,
                    0,
                ),
            )
        }
        assertNull(
            byteRange(
                "bytes=9223372036854775808-",
                10,
            ),
        )
        assertNull(
            byteRange(
                "bytes=-9223372036854775808",
                10,
            ),
        )
    }

    private fun request(method: String = "GET", range: String? = null): HttpURLConnection = (
        URL(
            server.register(
                uri,
                "video/mp4",
                "127.0.0.1",
            ),
        ).openConnection() as HttpURLConnection
        )
        .apply {
            requestMethod = method
            connectTimeout = 3000
            readTimeout = 3000
            range?.let {
                setRequestProperty(
                    "Range",
                    it,
                )
            }
        }

    private fun HttpURLConnection.useResponse(check: HttpURLConnection.() -> Unit) {
        try {
            check()
        } finally {
            disconnect()
        }
    }
}
