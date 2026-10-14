package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.room.Room
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.database.MediaDatabase
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import dev.anilbeesetti.nextplayer.core.media.services.TransferEvent
import dev.anilbeesetti.nextplayer.core.media.services.TransferMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.isNew
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 30], manifest = Config.NONE)
class LocalVaultRepositoryDatesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()

    @After
    fun tearDown() = runBlocking {
        database.hiddenVideoDao().getAll().first().forEach { File(it.vaultPath).delete() }
        database.close()
    }

    @Test
    fun `hide and restore preserve old dates even when MediaStore assigns fresh dates`() = runBlocking {
        val source = temporaryFolder.newFile("video.mp4").apply { writeText("video contents") }
        val original = Video.sample.copy(
            path = source.path,
            uriString = source.toUri().toString(),
            nameWithExtension = source.name,
            dateAdded = 1_600_000_000L,
            dateModified = 1_600_000_100L,
        )
        val repository = repository()
        repository.hideVideos(listOf(original))
        val hidden = database.hiddenVideoDao().getAll().first().single()
        assertEquals(original.dateAdded, hidden.dateAdded)
        assertEquals(original.dateModified, hidden.dateModified)

        val restoredFile = temporaryFolder.newFile("restored.mp4")
        val provider = RestoredVideoProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context.contentResolver).registerOutputStream(provider.restoredUri, restoredFile.outputStream())

        // Recreate the repository to exercise the persisted dates, not in-memory state.
        repository().unhideVideos(listOf(original.copy(id = hidden.id)))
        val state = database.mediumStateDao().get(provider.restoredUri.toString())
        val restored = restoredMediaVideo(provider.restoredUri).toVideo(state)

        assertEquals("video contents", restoredFile.readText())
        assertTrue(database.hiddenVideoDao().getAll().first().isEmpty())
        assertFalse(File(hidden.vaultPath).exists())
        assertEquals(original.dateAdded, restored.dateAdded)
        assertEquals(original.dateModified, restored.dateModified)
        assertFalse(restored.isNew())

        // A subsequent hide must keep the same dates as well.
        repository.hideVideos(listOf(restored.copy(path = restoredFile.path, uriString = restoredFile.toUri().toString())))
        val hiddenAgain = database.hiddenVideoDao().getAll().first().single()
        assertEquals(original.dateAdded, hiddenAgain.dateAdded)
        assertEquals(original.dateModified, hiddenAgain.dateModified)
    }

    @Test
    fun `failed restore keeps vault video and removes date overrides for rolled back item`() = runBlocking {
        val source = temporaryFolder.newFile("video.mp4").apply { writeText("video contents") }
        val video = Video.sample.copy(path = source.path, uriString = source.toUri().toString(), dateAdded = 123)
        val repository = repository()
        repository.hideVideos(listOf(video))
        val hidden = database.hiddenVideoDao().getAll().first().single()
        val provider = RestoredVideoProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context.contentResolver).registerOutputStream(
            provider.restoredUri,
            object : OutputStream() {
                override fun write(value: Int) = throw IOException("Unable to write restored video")
            },
        )

        repository.unhideVideos(listOf(video.copy(id = hidden.id)))

        assertEquals(listOf(hidden), database.hiddenVideoDao().getAll().first())
        assertTrue(File(hidden.vaultPath).exists())
        assertTrue(database.mediumStateDao().getAll().first().isEmpty())
        assertTrue(provider.deleted)
    }

    private fun repository() = LocalVaultRepository(
        database.hiddenVideoDao(),
        database.mediumStateDao(),
        MovingFilesService,
        context,
    )

    private fun restoredMediaVideo(uri: Uri) = MediaVideo(
        id = 1,
        uri = uri,
        path = "/storage/emulated/0/Movies/video.mp4",
        title = "video.mp4",
        parentPath = "/storage/emulated/0/Movies",
        displayName = "video.mp4",
        duration = 1000,
        size = 100,
        width = 320,
        height = 180,
        dateAdded = System.currentTimeMillis() / 1000L,
        dateModified = System.currentTimeMillis() / 1000L,
    )

    private class RestoredVideoProvider : ContentProvider() {
        val restoredUri = "content://media/external/video/media/1".toUri()
        var deleted = false

        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?) = restoredUri
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 1
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted = true
            return 1
        }

        override fun getType(uri: Uri) = "video/mp4"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    }

    private object MovingFilesService : MediaOperationsService {
        override fun initialize(activity: ComponentActivity) = Unit
        override suspend fun deleteMedia(uris: List<Uri>, permanently: Boolean) = false
        override suspend fun restoreMedia(uris: List<Uri>) = false
        override suspend fun renameMedia(uri: Uri, to: String) = false
        override suspend fun shareMedia(uris: List<Uri>) = Unit
        override suspend fun moveMedia(targets: Map<Uri, File>) = targets.mapValues { (uri, destination) ->
            destination.takeIf { File(requireNotNull(uri.path)).renameTo(it) }
        }

        override fun transferMedia(uris: List<Uri>, folderUri: Uri, mode: TransferMode): Flow<TransferEvent> = emptyFlow()
    }
}
