package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.os.Process
import android.os.storage.StorageManager
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.StorageVolumeBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], manifest = Config.NONE, shadows = [MediaStoreStorageTest.Scanner::class])
class MediaStoreStorageTest {
    private val application = RuntimeEnvironment.getApplication()
    private val context = object : ContextWrapper(application) {
        override fun getExternalFilesDirs(type: String?): Array<File?> = arrayOf(application.getExternalFilesDir(type))
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val provider = VideoProvider()
    private val service = MediaStoreMediaService(context, scope)
    private val usb = File(context.cacheDir, "USB").apply { mkdirs() }
    private val video = File(usb, "video.mp4").apply { writeText("video") }

    @Before
    fun setUp() {
        Scanner.paths.clear()
        Scanner.failure = null
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    @After
    fun tearDown() {
        scope.cancel()
        usb.deleteRecursively()
    }

    @Test
    fun `opening the library scans a USB volume omitted from app-specific directories`() = runBlocking {
        mountUsb()
        provider.video = video
        provider.requiredScanPath = usb.path
        val emissions = Channel<List<MediaVideo>>(Channel.UNLIMITED)
        val collector = launch { service.observeVideos().collect { emissions.send(it) } }
        try {
            withTimeout(5_000) {
                var videos = emissions.receive()
                while (videos.isEmpty()) videos = emissions.receive()
                assertEquals(video.path, videos.single().path)
                assertTrue(Scanner.paths.contains(usb.path))
                assertTrue(!Scanner.paths.contains(Environment.getExternalStorageDirectory().path))
            }
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `mount and removal update videos and folders without video-table notifications`() = runBlocking {
        for (folders in listOf(false, true)) {
            Scanner.paths.clear()
            provider.video = null
            provider.requiredScanPath = usb.path
            val emissions = Channel<List<*>>(Channel.UNLIMITED)
            val flow = if (folders) service.observeFolders() else service.observeVideos()
            val collector = launch { flow.collect { emissions.send(it) } }
            try {
                withTimeout(5_000) {
                    assertEquals(emptyList<Any>(), emissions.receive())
                    provider.video = video
                    sendStorageBroadcast(Intent.ACTION_MEDIA_MOUNTED)
                    assertEquals(1, emissions.receive().size)
                    assertTrue(Scanner.paths.contains(usb.path))
                    provider.video = null
                    sendStorageBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
                    assertEquals(emptyList<Any>(), emissions.receive())
                    provider.video = video
                    sendStorageBroadcast(Intent.ACTION_MEDIA_SCANNER_FINISHED)
                    assertEquals(1, emissions.receive().size)
                }
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun `a failed storage scan does not stop library updates`() = runBlocking {
        mountUsb()
        Scanner.failure = SecurityException("Drive became inaccessible")
        provider.video = video
        val emissions = Channel<List<MediaVideo>>(Channel.UNLIMITED)
        val collector = launch { service.observeVideos().collect { emissions.send(it) } }
        try {
            withTimeout(5_000) {
                assertEquals(video.path, emissions.receive().single().path)
                provider.video = null
                sendStorageBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
                assertEquals(emptyList<MediaVideo>(), emissions.receive())
                Scanner.failure = null
                provider.video = video
                provider.requiredScanPath = usb.path
                sendStorageBroadcast(Intent.ACTION_MEDIA_MOUNTED)
                assertEquals(video.path, emissions.receive().single().path)
            }
        } finally {
            collector.cancelAndJoin()
        }
    }

    private fun mountUsb() {
        shadowOf(context.getSystemService(StorageManager::class.java)).addStorageVolume(
            StorageVolumeBuilder("USB", usb, "USB", Process.myUserHandle(), Environment.MEDIA_MOUNTED)
                .setIsPrimary(false)
                .setIsRemovable(true)
                .build(),
        )
    }

    private fun sendStorageBroadcast(action: String) {
        context.sendBroadcast(Intent(action, Uri.fromFile(usb)))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Implements(MediaScannerConnection::class)
    class Scanner {
        companion object {
            val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
            var failure: RuntimeException? = null

            @JvmStatic
            @Implementation
            fun scanFile(context: Context, paths: Array<String>, mimeTypes: Array<String>?, callback: MediaScannerConnection.OnScanCompletedListener?) {
                failure?.let { throw it }
                paths.forEach {
                    this.paths.add(it)
                    callback?.onScanCompleted(it, null)
                }
            }
        }
    }

    private class VideoProvider : ContentProvider() {
        @Volatile
        var video: File? = null
        var requiredScanPath: String? = null

        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            return MatrixCursor(projection).apply {
                video?.takeIf { requiredScanPath == null || Scanner.paths.contains(requiredScanPath) }?.let {
                    addRow(arrayOf<Any>(1L, it.path, it.name, 100L, 1080, 1920, it.length(), 1L, 2L))
                }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
