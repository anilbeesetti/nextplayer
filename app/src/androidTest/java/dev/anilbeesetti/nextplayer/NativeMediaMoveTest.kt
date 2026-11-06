package dev.anilbeesetti.nextplayer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import dev.anilbeesetti.nextplayer.core.media.services.LocalMediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.TransferEvent
import dev.anilbeesetti.nextplayer.core.media.services.TransferMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMediaMoveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver

    @Test
    fun nativeMoveRetainsMediaIdentityDatesAndContents() = verifyMove(collision = false)

    @Test
    fun nativeMoveKeepsExistingDestinationAndRetainsOriginalDates() = verifyMove(collision = true)

    private fun verifyMove(collision: Boolean) = runBlocking {
        val readPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        shell("pm grant ${context.packageName} $readPermission")
        if (Build.VERSION.SDK_INT < 30) {
            shell("pm grant ${context.packageName} ${Manifest.permission.WRITE_EXTERNAL_STORAGE}")
        }
        val relativeRoot = "Movies/native-move-${UUID.randomUUID()}"
        val root = File(
            Environment.getExternalStorageDirectory(),
            relativeRoot,
        )
        val sourceFolder = File(
            root,
            "source",
        ).apply { mkdirs() }
        val targetFolder = File(
            root,
            "target",
        ).apply { mkdirs() }
        val source = File(
            sourceFolder,
            "video.mp4",
        )
        var sourceUri: Uri? = null
        var collisionUri: Uri? = null
        try {
            sourceUri = createVideo(source)
            resolver.openOutputStream(sourceUri)!!.use { output ->
                InstrumentationRegistry.getInstrumentation().context.assets.open(
                    "thumbnail-test.mp4",
                ).use {
                    it.copyTo(output)
                }
            }
            assertTrue(
                "Source path differs from MediaStore: ${snapshot(sourceUri).path}",
                source.isFile,
            )
            val historicalTimestampSet = source.setLastModified(1_600_000_000_000L)
            val originalModified = source.lastModified()
            assertTrue(originalModified > 0)
            // Legacy emulated storage can reject utime; separate creation and move timestamps.
            if (!historicalTimestampSet) delay(timeMillis = 2_100)
            withTimeout(timeMillis = 10_000) { context.scanPaths(listOf(source.path)) }
            val before = snapshot(sourceUri)
            val bytes = source.readBytes()
            if (collision) {
                collisionUri = createVideo(
                    File(
                        targetFolder,
                        source.name,
                    ),
                )
                resolver.openOutputStream(collisionUri)!!.use {
                    it.write("existing destination".toByteArray())
                }
            }
            context.grantUriPermission(
                context.packageName,
                sourceUri,
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val result = withTimeout(timeMillis = 10_000) {
                LocalMediaOperationsService(context).transferMedia(
                    uris = listOf(sourceUri),
                    folderUri = DocumentsContract.buildTreeDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:$relativeRoot/target",
                    ),
                    mode = TransferMode.MOVE,
                ).filterIsInstance<TransferEvent.Completed>().single().result
            }
            assertEquals(
                1,
                result.succeeded,
            )
            assertEquals(
                0,
                result.failed,
            )
            val after = snapshot(sourceUri)
            assertEquals(
                before.id,
                after.id,
            )
            assertEquals(
                before.added,
                after.added,
            )
            assertEquals(
                before.modified,
                after.modified,
            )
            assertEquals(
                originalModified / 1000,
                after.modified,
            )
            assertFalse(source.exists())
            val destination = File(after.path)
            assertEquals(
                targetFolder,
                destination.parentFile,
            )
            assertEquals(
                originalModified,
                destination.lastModified(),
            )
            assertTrue(bytes.contentEquals(destination.readBytes()))
            if (collision) {
                assertEquals(
                    "existing destination",
                    File(
                        targetFolder,
                        "video.mp4",
                    ).readText(),
                )
                assertTrue(destination.name != "video.mp4")
            }
        } finally {
            sourceUri?.let {
                resolver.delete(
                    it,
                    null,
                    null,
                )
            }
            collisionUri?.let {
                resolver.delete(
                    it,
                    null,
                    null,
                )
            }
            root.deleteRecursively()
        }
    }

    private fun createVideo(file: File): Uri = checkNotNull(
        resolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    file.name,
                )
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "video/mp4",
                )
                if (Build.VERSION.SDK_INT >= 29) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        file.parentFile!!.relativeTo(
                            Environment.getExternalStorageDirectory(),
                        ).path,
                    )
                } else {
                    put(
                        MediaStore.MediaColumns.DATA,
                        file.path,
                    )
                }
            },
        ),
    )

    private data class Snapshot(val id: Long, val added: Long, val modified: Long, val path: String)

    private fun snapshot(uri: Uri): Snapshot = resolver.query(
        uri,
        arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA,
        ),
        null,
        null,
        null,
    )!!.use {
        assertTrue(
            "Original MediaStore URI must still resolve",
            it.moveToFirst(),
        )
        Snapshot(
            it.getLong(0),
            it.getLong(1),
            it.getLong(2),
            it.getString(3),
        )
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }
}
