package dev.anilbeesetti.nextplayer

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.services.MediaStoreMediaService
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import io.github.anilbeesetti.nextlib.mediainfo.MediaThumbnailRetriever
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CompatibilityPickerTest {
    @Test
    fun pickerFillsMissingDurationAndDimensions() = runBlocking {
        withRecording { uri ->
            val service = GlobalContext.get().get<MediaService>()
            val video = checkNotNull(service.findVideo(uri))
            assertTrue(
                "Missing duration: $video",
                video.duration > 0,
            )
            assertTrue(
                "Missing dimensions: $video",
                video.width > 0 && video.height > 0,
            )
            assertRecordingMetadata(
                video.duration,
                video.width,
                video.height,
            )
            assertEquals(
                video,
                service.fetchVideos(video.parentPath).single { it.uri == uri },
            )
            assertTrue(
                checkNotNull(service.findFolder(video.parentPath)).totalDuration >= video.duration,
            )
        }
    }

    @Test
    fun metadataCachePreservesKnownValuesAndRetriesChangedOrTemporarilyUnreadableFiles() =
        runBlocking {
            withRecording { uri ->
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val resolver = context.contentResolver
                var modified = 0L
                var known = false
                var unreadable = false
                var shortRead = false
                var opens = 0
                val partial = File.createTempFile(
                    "partial-",
                    ".h264",
                    context.cacheDir,
                )
                // The fixture's first SPS and partial frame have dimensions but no container timing.
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("header-program-stream.mp4").use {
                        partial.writeBytes(
                            it.readBytes().copyOfRange(
                                4543,
                                5567,
                            ),
                        )
                    }
                val provider = object : ContentProvider() {
                    override fun onCreate() = true
                    override fun query(
                        uri: Uri,
                        projection: Array<out String>?,
                        selection: String?,
                        selectionArgs: Array<out String>?,
                        sortOrder: String?,
                    ): Cursor = object : CursorWrapper(
                        resolver.query(
                            uri,
                            projection,
                            selection,
                            selectionArgs,
                            sortOrder,
                        ),
                    ) {
                        override fun getLong(columnIndex: Int): Long =
                            when (getColumnName(columnIndex)) {
                                MediaStore.Video.Media.DATE_MODIFIED -> super.getLong(
                                    columnIndex,
                                ) +
                                    modified

                                MediaStore.Video.Media.DURATION -> if (known) 1234 else 0

                                else -> super.getLong(columnIndex)
                            }
                        override fun getInt(columnIndex: Int): Int =
                            when (getColumnName(columnIndex)) {
                                MediaStore.Video.Media.WIDTH -> if (known) 42 else 0
                                MediaStore.Video.Media.HEIGHT -> if (known) 24 else 0
                                else -> super.getInt(columnIndex)
                            }
                    }
                    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                        opens++
                        if (unreadable) throw FileNotFoundException("Temporarily unreadable")
                        if (shortRead) {
                            return ParcelFileDescriptor.open(
                                partial,
                                ParcelFileDescriptor.MODE_READ_ONLY,
                            )
                        }
                        return checkNotNull(
                            resolver.openFileDescriptor(
                                uri,
                                mode,
                            ),
                        )
                    }
                    override fun getType(uri: Uri): String? = null
                    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
                    override fun delete(
                        uri: Uri,
                        selection: String?,
                        selectionArgs: Array<out String>?,
                    ) = 0
                    override fun update(
                        uri: Uri,
                        values: ContentValues?,
                        selection: String?,
                        selectionArgs: Array<out String>?,
                    ) = 0
                }
                val wrapped = object : ContextWrapper(context) {
                    override fun getContentResolver(): ContentResolver =
                        ContentResolver.wrap(provider)
                }
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val service = MediaStoreMediaService(
                    wrapped,
                    scope,
                )
                try {
                    val first = checkNotNull(service.findVideo(uri))
                    assertTrue(first.duration > 0)
                    assertEquals(
                        first,
                        service.findVideo(uri),
                    )
                    assertEquals(
                        1,
                        opens,
                    )
                    modified++
                    assertTrue(checkNotNull(service.findVideo(uri)).duration > 0)
                    assertEquals(
                        2,
                        opens,
                    )
                    known = true
                    val existing = checkNotNull(service.findVideo(uri))
                    assertEquals(
                        1234L,
                        existing.duration,
                    )
                    assertEquals(
                        42,
                        existing.width,
                    )
                    assertEquals(
                        24,
                        existing.height,
                    )
                    assertEquals(
                        2,
                        opens,
                    )
                    known = false
                    modified++
                    unreadable = true
                    assertEquals(
                        0L,
                        checkNotNull(service.findVideo(uri)).duration,
                    )
                    unreadable = false
                    assertTrue(checkNotNull(service.findVideo(uri)).duration > 0)
                    assertEquals(
                        4,
                        opens,
                    )
                    modified++
                    shortRead = true
                    val incomplete = checkNotNull(service.findVideo(uri))
                    assertEquals(
                        0L,
                        incomplete.duration,
                    )
                    assertEquals(
                        160,
                        incomplete.width,
                    )
                    assertEquals(
                        120,
                        incomplete.height,
                    )
                    shortRead = false
                    val restored = checkNotNull(service.findVideo(uri))
                    assertTrue(restored.duration > 0)
                    assertEquals(
                        restored,
                        service.findVideo(uri),
                    )
                    assertEquals(
                        6,
                        opens,
                    )
                } finally {
                    scope.cancel()
                    partial.delete()
                }
            }
        }

    @Test
    fun pickerDecodesAllThumbnailStrategiesWithoutCache() = runBlocking {
        withRecording { uri ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            for (strategy in listOf(
                ThumbnailStrategy.FirstFrame,
                ThumbnailStrategy.FrameAtPercentage(),
                ThumbnailStrategy.Hybrid(),
            )) {
                val loader = ImageLoader.Builder(context)
                    .components { add(VideoThumbnailDecoder.Factory { strategy }) }.build()
                try {
                    val result = loader.execute(
                        ImageRequest.Builder(context).data(uri).size(
                            160,
                            120,
                        )
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.DISABLED).build(),
                    )
                    assertTrue(
                        "$strategy: $result",
                        result is SuccessResult,
                    )
                    assertTrue((result as SuccessResult).image.width > 0 && result.image.height > 0)
                } finally {
                    loader.shutdown()
                }
            }
        }
    }

    @Test
    fun ffmpegReadsMetadataFromSeekableDescriptor() = runBlocking {
        withRecording { uri ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val resolver = context.contentResolver
            resolver.openFileDescriptor(
                uri,
                "r",
            )!!.use { descriptor ->
                val info = checkNotNull(MediaInfoBuilder().from(descriptor).build())
                try {
                    val stream = checkNotNull(info.videoStream)
                    assertRecordingMetadata(
                        info.duration,
                        stream.frameWidth,
                        stream.frameHeight,
                    )
                } finally {
                    info.release()
                }
            }
        }
    }

    @Test
    fun ffmpegReadsThumbnailFromDescriptor() = runBlocking {
        withRecording { uri ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val resolver = context.contentResolver
            resolver.openFileDescriptor(
                uri,
                "r",
            )!!.use { descriptor ->
                MediaThumbnailRetriever().use { retriever ->
                    retriever.setDataSource(descriptor)
                    descriptor.close()
                    assertTrue(retriever.durationMillis > 0)
                    for (timeUs in listOf(
                        0L,
                        retriever.durationMillis * 1000 / 3,
                    )) {
                        val frame = retriever.getFrameAtTime(timeUs)
                        assertNotNull(
                            "FFmpeg did not decode a frame at $timeUs",
                            frame,
                        )
                        frame?.recycle()
                    }
                }
            }
        }
    }

    private suspend fun withRecording(block: suspend (Uri) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val supplied = InstrumentationRegistry.getArguments().getString("compatibilityVideo")
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        "picker-${UUID.randomUUID()}.mp4",
                    )
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Movies/nextplayer-picker-test",
                    )
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        "video/mp4",
                    )
                    put(
                        MediaStore.MediaColumns.IS_PENDING,
                        1,
                    )
                },
            ),
        )
        try {
            val input = supplied?.let { File(it).inputStream() }
                ?: instrumentation.context.assets.open("header-program-stream.mp4")
            input.use { source ->
                resolver.openOutputStream(uri)!!.use { source.copyTo(it) }
            }
            resolver.update(
                uri,
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.IS_PENDING,
                        0,
                    )
                },
                null,
                null,
            )
            block(uri)
        } finally {
            resolver.delete(
                uri,
                null,
                null,
            )
        }
    }

    private fun assertRecordingMetadata(duration: Long, width: Int, height: Int) {
        val original = InstrumentationRegistry.getArguments()
            .getString("compatibilityVideo") != null
        val expectedDuration = if (original) 815_328L else 3_000L
        assertTrue(
            "Duration was ${duration}ms",
            kotlin.math.abs(duration - expectedDuration) < 500,
        )
        assertEquals(
            if (original) 960 else 160,
            width,
        )
        assertEquals(
            if (original) 1080 else 120,
            height,
        )
    }
}
