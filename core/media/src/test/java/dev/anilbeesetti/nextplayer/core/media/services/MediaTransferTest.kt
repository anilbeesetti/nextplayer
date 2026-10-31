package dev.anilbeesetti.nextplayer.core.media.services

import android.app.PendingIntent
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [30],
    manifest = Config.NONE,
    shadows = [MediaTransferTest.MediaStoreShadow::class],
)
class MediaTransferTest {
    private var writeGranted = true
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun checkUriPermission(uri: Uri?, pid: Int, uid: Int, modeFlags: Int): Int =
            if (writeGranted) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
    }
    private val root = File(
        context.cacheDir,
        "transfer-test",
    )
    private val source = File(
        root,
        "source/video.mp4",
    )
    private val provider = TransferProvider()
    private val sourceUri = Uri.parse("content://media/external/video/media/1")
    private val folderUri = DocumentsContract.buildTreeDocumentUri(
        "transfer.documents",
        "target",
    )
    private val service = LocalMediaOperationsService(context = context)

    @Before
    fun setUp() {
        source.parentFile!!.mkdirs()
        source.writeBytes(array = ByteArray(size = DEFAULT_BUFFER_SIZE * 2) { it.toByte() })
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = "media;transfer.documents"
            },
        )
        ShadowContentResolver.registerProviderInternal(
            "media",
            provider,
        )
        ShadowContentResolver.registerProviderInternal(
            "transfer.documents",
            provider,
        )
        shadowOf(context.contentResolver).registerInputStreamSupplier(sourceUri) {
            source.inputStream()
        }
        MediaStoreShadow.requests.clear()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `move removes original directly without trash or another permission request`() =
        runBlocking {
            val expected = source.readBytes()
            val result = transfer(TransferMode.MOVE)
            assertEquals(
                TransferResult(
                    succeeded = 1,
                    failed = 0,
                ),
                result,
            )
            assertFalse(source.exists())
            assertTrue(expected.contentEquals(provider.created.single().readBytes()))
            assertEquals(
                1,
                provider.deletes,
            )
            assertTrue(MediaStoreShadow.requests.isEmpty())
        }

    @Test
    fun `copy keeps original and needs no write permission`() = runBlocking {
        writeGranted = false
        val result = transfer(TransferMode.COPY)
        assertEquals(
            1,
            result.succeeded,
        )
        assertTrue(source.exists())
        assertEquals(
            0,
            provider.deletes,
        )
        assertTrue(MediaStoreShadow.requests.isEmpty())
    }

    @Test
    fun `unavailable consent creates no copies and batches sources before moving`() = runBlocking {
        writeGranted = false
        val second = Uri.parse("content://media/external/video/media/2")
        val result = service.transferMedia(
            uris = listOf(
                sourceUri,
                second,
            ),
            folderUri = folderUri,
            mode = TransferMode.MOVE,
        ).filterIsInstance<TransferEvent.Completed>().single().result
        assertEquals(
            TransferResult(
                succeeded = 0,
                failed = 2,
            ),
            result,
        )
        assertEquals(
            listOf(
                listOf(
                    sourceUri,
                    second,
                ),
            ),
            MediaStoreShadow.requests,
        )
        assertTrue(provider.created.isEmpty())
        assertTrue(source.exists())
    }

    @Test
    fun `same folder move skips consent and leaves source alone`() = runBlocking {
        writeGranted = false
        provider.sameFolder = true
        assertEquals(
            TransferResult(
                succeeded = 0,
                failed = 0,
                sameFolderSkipped = 1,
            ),
            transfer(TransferMode.MOVE),
        )
        assertTrue(MediaStoreShadow.requests.isEmpty())
        assertTrue(provider.created.isEmpty())
        assertTrue(source.exists())
    }

    @Test
    fun `incomplete copy keeps original and removes destination`() = runBlocking {
        shadowOf(context.contentResolver).registerInputStreamSupplier(sourceUri) {
            ByteArrayInputStream(
                byteArrayOf(
                    1,
                    2,
                    3,
                ),
            )
        }
        val result = transfer(TransferMode.MOVE)
        assertEquals(
            0,
            result.succeeded,
        )
        assertEquals(
            1,
            result.failed,
        )
        assertTrue(source.exists())
        assertFalse(provider.created.single().exists())
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    fun `canceled copy keeps original and removes partial destination`() {
        shadowOf(context.contentResolver).registerInputStreamSupplier(sourceUri) {
            object : InputStream() {
                override fun read(): Int = throw CancellationException("Canceled copy")
            }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { transfer(TransferMode.MOVE) }
        }
        assertTrue(source.exists())
        assertFalse(provider.created.single().exists())
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    fun `failed source removal preserves both files and reports originals remain`() = runBlocking {
        provider.allowDelete = false
        val result = transfer(TransferMode.MOVE)
        assertTrue(result.originalsNotDeleted)
        assertTrue(source.exists())
        assertTrue(source.readBytes().contentEquals(provider.created.single().readBytes()))
        assertTrue(MediaStoreShadow.requests.isEmpty())
    }

    @Test
    fun `name collision preserves the existing destination`() = runBlocking {
        val existing = File(
            root,
            "target/video.mp4",
        ).apply {
            parentFile!!.mkdirs()
            writeText(text = "existing video")
        }
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertEquals(
            "existing video",
            existing.readText(),
        )
        assertTrue(provider.created.single().name != existing.name)
    }

    private suspend fun transfer(mode: TransferMode): TransferResult = service.transferMedia(
        uris = listOf(sourceUri),
        folderUri = folderUri,
        mode = mode,
    ).filterIsInstance<TransferEvent.Completed>().single().result

    private inner class TransferProvider : ContentProvider() {
        val created = mutableListOf<File>()
        var deletes = 0
        var allowDelete = true
        var sameFolder = false

        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = "video/mp4"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = error("Move must never trash an original")

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(projection).apply {
            addRow(
                projection!!.map {
                    when (it) {
                        OpenableColumns.DISPLAY_NAME -> source.name

                        MediaStore.MediaColumns.DATA -> if (sameFolder) {
                            if (uri.authority == "media") source.path else source.parent
                        } else {
                            null
                        }

                        else -> 1L
                    }
                },
            )
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(
                source,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deletes++
            return if (allowDelete && source.delete()) 1 else 0
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            val uri = DocumentsContract.buildDocumentUri(
                "transfer.documents",
                "copy-${created.size}",
            )
            when (method) {
                "android:createDocument" -> {
                    var destination = File(
                        root,
                        "target/${source.name}",
                    )
                    if (destination.exists()) {
                        destination = File(
                            root,
                            "target/video (1).mp4",
                        )
                    }
                    destination.parentFile!!.mkdirs()
                    destination.createNewFile()
                    created += destination
                    shadowOf(
                        this@MediaTransferTest.context.contentResolver,
                    ).registerOutputStreamSupplier(uri) {
                        destination.outputStream()
                    }
                }

                "android:deleteDocument" -> created.last().delete()

                else -> error("Unexpected document operation: $method")
            }
            return Bundle().apply {
                putParcelable(
                    "uri",
                    uri,
                )
            }
        }
    }

    @Implements(MediaStore::class)
    class MediaStoreShadow {
        companion object {
            val requests = mutableListOf<List<Uri>>()

            @JvmStatic
            @Implementation
            fun createWriteRequest(
                resolver: ContentResolver,
                uris: Collection<Uri>,
            ): PendingIntent {
                requests += uris.toList()
                return PendingIntent.getActivity(
                    RuntimeEnvironment.getApplication(),
                    0,
                    Intent("write"),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            }
        }
    }
}
