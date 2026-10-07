package dev.anilbeesetti.nextplayer

import android.Manifest
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class VideoThumbnailDecoderTest {
    @Test
    fun contentVideoWithInaccessiblePathDecodesWithoutCachedThumbnail() = runBlocking {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val file = File(targetContext.cacheDir, "thumbnail-test.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("thumbnail-test.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true

            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

            override fun getType(uri: Uri): String = "video/mp4"

            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor = MatrixCursor(arrayOf(MediaStore.Video.Media.DATA)).apply {
                addRow(arrayOf("/inaccessible/.trashed-1234567890-video.mp4"))
            }

            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(targetContext) {
            override fun getContentResolver(): ContentResolver = resolver
        }

        try {
            assertDecodesAllStrategies(context, "content://media/external/video/media/1")
        } finally {
            file.delete()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun trashedMediaStoreVideoDecodesWithoutCachedThumbnail() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "nextplayer-thumbnail-${UUID.randomUUID()}.mp4"
        val uri = checkNotNull(
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                },
            ),
        )
        val readPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.adoptShellPermissionIdentity(readPermission)
        try {
            instrumentation.context.assets.open("thumbnail-test.mp4").use { input ->
                context.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
            }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            // Transfer ownership so the test covers another app's trashed video.
            shell("content update --uri $uri --bind owner_package_name:s:com.android.shell --bind is_trashed:i:1")
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED, MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals("com.android.shell", cursor.getString(1))
            }

            assertDecodesAllStrategies(context, uri.toString())
        } finally {
            shell("content delete --uri $uri")
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private suspend fun assertDecodesAllStrategies(context: Context, uri: String) {
        for (strategy in listOf(ThumbnailStrategy.FirstFrame, ThumbnailStrategy.FrameAtPercentage(), ThumbnailStrategy.Hybrid())) {
            val imageLoader = ImageLoader.Builder(context)
                .components { add(VideoThumbnailDecoder.Factory { strategy }) }
                .build()
            try {
                val result = imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(uri)
                        .size(160, 90)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                )
                assertTrue("$strategy: $result", result is SuccessResult)
                assertTrue((result as SuccessResult).image.width > 0)
                assertTrue(result.image.height > 0)
            } finally {
                imageLoader.shutdown()
            }
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }
}
