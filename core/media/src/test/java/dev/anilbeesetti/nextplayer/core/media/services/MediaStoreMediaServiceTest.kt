package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.extensions.VIDEO_COLLECTION_URI
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class MediaStoreMediaServiceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = RuntimeEnvironment.getApplication()
    private val provider = VideoProvider()
    private val service = MediaStoreMediaService(context, scope)

    @Before
    fun setUp() {
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `one-shot reads preserve missing volume failures`() {
        val failure = IllegalArgumentException("Volume external_primary not found")
        provider.failure = failure
        assertEquals(failure.message, assertThrows(IllegalArgumentException::class.java) { runBlocking { service.fetchVideos() } }.message)
        assertEquals(failure.message, assertThrows(IllegalArgumentException::class.java) { runBlocking { service.fetchFolders() } }.message)
    }

    @Test
    fun `observers recover after a missing volume becomes available`() = runBlocking {
        val video = File(context.cacheDir, "video.mp4").apply { writeText("video") }
        try {
            val flows = listOf<Flow<List<*>>>(service.observeVideos(), service.observeFolders(), service.observeTrashVideos())
            for ((index, flow) in flows.withIndex()) {
                val volume = if (index == 1) "1234-5678" else "external_primary"
                provider.failure = IllegalArgumentException("Volume $volume not found")
                provider.video = video
                val emissions = Channel<List<*>>(Channel.UNLIMITED)
                val collector = launch { flow.collect { emissions.send(it) } }
                try {
                    withTimeout(5_000) {
                        assertEquals(emptyList<Any>(), emissions.receive())
                        provider.failure = null
                        context.contentResolver.notifyChange(VIDEO_COLLECTION_URI, null)
                        assertEquals(1, emissions.receive().size)
                    }
                } finally {
                    collector.cancelAndJoin()
                }
            }
        } finally {
            video.delete()
        }
    }

    @Test
    fun `other provider failures are preserved`() {
        for (failure in listOf(IllegalArgumentException("Invalid projection"), SecurityException("Permission denial"))) {
            provider.failure = failure
            assertEquals(
                failure.message,
                assertThrows(failure.javaClass) { runBlocking { service.observeVideos().first() } }.message,
            )
        }
        provider.failure = null
        provider.returnNull = true
        assertThrows(IllegalStateException::class.java) { runBlocking { service.fetchVideos() } }
    }

    private class VideoProvider : ContentProvider() {
        @Volatile
        var failure: RuntimeException? = null
        var video: File? = null
        var returnNull = false

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            failure?.let { throw it }
            if (returnNull) return null
            return MatrixCursor(projection).apply {
                video?.let { addRow(arrayOf<Any>(1L, it.path, it.name, 100L, 1080, 1920, it.length(), 1L)) }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
