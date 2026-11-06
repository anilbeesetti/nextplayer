package dev.anilbeesetti.nextplayer.core.media.services

import android.Manifest
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
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
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
    sdk = [24, 28, 29, 30],
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
    private var folderUri = DocumentsContract.buildTreeDocumentUri(
        "transfer.documents",
        "target",
    )
    private val service = LocalMediaOperationsService(context = context)

    @Before
    fun setUp() {
        shadowOf(
            RuntimeEnvironment.getApplication(),
        ).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        source.parentFile!!.mkdirs()
        source.writeBytes(array = ByteArray(size = DEFAULT_BUFFER_SIZE * 2) { it.toByte() })
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = "media;transfer.documents;com.android.externalstorage.documents"
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
        ShadowContentResolver.registerProviderInternal(
            "com.android.externalstorage.documents",
            provider,
        )
        MediaStoreShadow.requests.clear()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        nativeFolder.deleteRecursively()
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
        shadowOf(
            RuntimeEnvironment.getApplication(),
        ).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
    @Config(sdk = [24, 28, 29])
    fun `legacy move without storage write access creates no copies`() = runBlocking {
        shadowOf(
            RuntimeEnvironment.getApplication(),
        ).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val result = transfer(TransferMode.MOVE)
        assertEquals(
            TransferResult(
                succeeded = 0,
                failed = 1,
            ),
            result,
        )
        assertTrue(source.exists())
        assertTrue(provider.created.isEmpty())
        assertTrue(MediaStoreShadow.requests.isEmpty())
    }

    @Test
    @Config(sdk = [30])
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
        shadowOf(
            RuntimeEnvironment.getApplication(),
        ).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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

    private val nativeFolder = File(
        Environment.getExternalStorageDirectory(),
        "Movies/transfer-native-test",
    )

    private fun useNativeFolder(
        volume: String = "primary",
        relativePath: String = "Movies/transfer-native-test",
    ) {
        provider.nativeMode = true
        nativeFolder.mkdirs()
        folderUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "$volume:$relativePath",
        )
    }

    @Test
    fun `native move preserves timestamps without creating or deleting media`() = runBlocking {
        useNativeFolder()
        assertTrue(source.setLastModified(1_600_000_000_000L))
        val expected = source.readBytes()
        assertEquals(
            TransferResult(
                succeeded = 1,
                failed = 0,
            ),
            transfer(TransferMode.MOVE),
        )
        assertFalse(source.exists())
        assertTrue(expected.contentEquals(provider.currentFile.readBytes()))
        assertEquals(
            1_600_000_000_000L,
            provider.currentFile.lastModified(),
        )
        assertEquals(
            1,
            provider.updates,
        )
        assertTrue(provider.created.isEmpty())
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    fun `native name collision keeps existing destination intact`() = runBlocking {
        useNativeFolder()
        File(
            nativeFolder,
            source.name,
        ).writeText("existing video")
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertEquals(
            "existing video",
            File(
                nativeFolder,
                source.name,
            ).readText(),
        )
        assertEquals(
            "video (1).mp4",
            provider.currentFile.name,
        )
        assertTrue(provider.created.isEmpty())
    }

    @Test
    @Config(sdk = [29, 30])
    fun `native move supports a matching secondary volume`() = runBlocking {
        useNativeFolder(volume = "ABCD-1234")
        provider.volume = "abcd-1234"
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertEquals(
            "Movies/transfer-native-test/",
            provider.relativePath,
        )
        assertTrue(provider.created.isEmpty())
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    @Config(sdk = [29, 30])
    fun `secondary volume same folder move skips consent`() = runBlocking {
        useNativeFolder(volume = "ABCD-1234")
        provider.volume = "abcd-1234"
        provider.relativePath = "Movies/transfer-native-test/"
        writeGranted = false
        assertEquals(
            TransferResult(
                succeeded = 0,
                failed = 0,
                sameFolderSkipped = 1,
            ),
            transfer(TransferMode.MOVE),
        )
        assertEquals(
            0,
            provider.updates,
        )
        assertTrue(MediaStoreShadow.requests.isEmpty())
        assertTrue(source.exists())
    }

    @Test
    @Config(sdk = [29, 30])
    fun `different volume falls back to a verified copy`() = runBlocking {
        useNativeFolder()
        provider.volume = "abcd-1234"
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertEquals(
            0,
            provider.updates,
        )
        assertEquals(
            1,
            provider.created.size,
        )
        assertEquals(
            1,
            provider.deletes,
        )
    }

    @Test
    fun `invalid native destination path uses the document provider`() = runBlocking {
        useNativeFolder(relativePath = "../Movies/target")
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertEquals(
            0,
            provider.updates,
        )
        assertEquals(
            1,
            provider.created.size,
        )
    }

    @Test
    fun `failed native update falls back only while source remains intact`() = runBlocking {
        useNativeFolder()
        provider.failUpdate = true
        val expected = source.readBytes()
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertTrue(expected.contentEquals(provider.created.single().readBytes()))
        assertTrue(nativeFolder.listFiles().orEmpty().isEmpty())
    }

    @Test
    @Config(sdk = [29, 30])
    fun `provider error after a completed native move never copies or deletes it`() = runBlocking {
        useNativeFolder()
        provider.throwAfterMove = true
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertTrue(provider.currentFile.exists())
        assertTrue(provider.created.isEmpty())
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    @Config(sdk = [29, 30])
    fun `provider error after file movement with stale metadata never retries from stale uri`() =
        runBlocking {
            useNativeFolder()
            provider.failAfterMove = true
            assertEquals(
                1,
                transfer(TransferMode.MOVE).failed,
            )
            assertTrue(
                File(
                    nativeFolder,
                    source.name,
                ).exists(),
            )
            assertFalse(source.exists())
            assertTrue(provider.created.isEmpty())
            assertEquals(
                0,
                provider.deletes,
            )
        }

    @Test
    @Config(sdk = [24, 28])
    fun `legacy update applied before error leaves the committed move intact`() = runBlocking {
        useNativeFolder()
        provider.throwAfterMove = true
        assertEquals(
            1,
            transfer(TransferMode.MOVE).succeeded,
        )
        assertFalse(source.exists())
        assertTrue(provider.currentFile.exists())
        assertTrue(provider.created.isEmpty())
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    @Config(sdk = [24, 28])
    fun `legacy rollback failure preserves moved file and replacement source`() = runBlocking {
        useNativeFolder()
        provider.failUpdate = true
        provider.replaceSourceOnFailure = true
        val expected = source.readBytes()
        assertEquals(
            1,
            transfer(TransferMode.MOVE).failed,
        )
        assertEquals(
            "replacement",
            source.readText(),
        )
        assertTrue(
            expected.contentEquals(
                File(
                    nativeFolder,
                    source.name,
                ).readBytes(),
            ),
        )
        assertTrue(provider.created.isEmpty())
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    fun `cancellation before native move leaves the source untouched`() = runBlocking {
        useNativeFolder()
        lateinit var job: Job
        provider.onSourceRead = { job.cancel() }
        job = launch { transfer(TransferMode.MOVE) }
        job.join()
        assertTrue(source.exists())
        assertTrue(provider.created.isEmpty())
        assertEquals(
            0,
            provider.updates,
        )
        assertEquals(
            0,
            provider.deletes,
        )
    }

    @Test
    fun `cancellation after native move keeps the committed file`() = runBlocking {
        useNativeFolder()
        lateinit var job: Job
        provider.afterUpdate = { job.cancel() }
        job = launch { transfer(TransferMode.MOVE) }
        job.join()
        assertFalse(source.exists())
        assertTrue(provider.currentFile.exists())
        assertTrue(provider.created.isEmpty())
        assertEquals(
            0,
            provider.deletes,
        )
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
        var nativeMode = false
        var currentFile = source
        var volume = "external_primary"
        var relativePath = "Movies/source/"
        var updates = 0
        var failUpdate = false
        var throwAfterMove = false
        var failAfterMove = false
        var replaceSourceOnFailure = false
        var onSourceRead: (() -> Unit)? = null
        var afterUpdate: (() -> Unit)? = null

        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = "video/mp4"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            check(nativeMode)
            check(!values!!.containsKey(MediaStore.MediaColumns.IS_TRASHED))
            updates++
            if (failUpdate) {
                if (replaceSourceOnFailure) source.writeText("replacement")
                return 0
            }
            if (values.containsKey(MediaStore.MediaColumns.RELATIVE_PATH)) {
                var destination = File(
                    nativeFolder,
                    currentFile.name,
                )
                if (destination.exists()) {
                    destination = File(
                        nativeFolder,
                        "video (1).mp4",
                    )
                }
                check(currentFile.renameTo(destination))
                if (failAfterMove) return 0
                currentFile = destination
                relativePath = values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                currentFile = File(values.getAsString(MediaStore.MediaColumns.DATA))
            }
            afterUpdate?.invoke()
            if (throwAfterMove) throw IllegalStateException("Failed after moving")
            return 1
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(projection).apply {
            if (uri.authority == "media" && projection!!.contains(MediaStore.MediaColumns.DATA)) {
                onSourceRead?.invoke()
            }
            addRow(
                projection!!.map {
                    when (it) {
                        OpenableColumns.DISPLAY_NAME -> source.name

                        MediaStore.MediaColumns.VOLUME_NAME -> volume

                        MediaStore.MediaColumns.RELATIVE_PATH -> relativePath

                        MediaStore.MediaColumns.DATA -> if (nativeMode &&
                            uri.authority == "media"
                        ) {
                            currentFile.path
                        } else if (sameFolder) {
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
