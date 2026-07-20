package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.database.dao.HiddenVideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.HiddenVideoEntity
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.TransferEvent
import dev.anilbeesetti.nextplayer.core.media.services.TransferMode
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVaultRepositoryTest {

    @Test
    fun hideVideosKeepsSourceWhenDatabaseInsertFails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "issue-1828-${UUID.randomUUID()}.mp4"
        val sourceFile = File(context.cacheDir, fileName).apply { writeText("original video") }
        val vaultFile = File(File(context.getExternalFilesDir(null), "vault"), fileName)
        val mediaOperations = MovingMediaOperationsService()
        val repository = LocalVaultRepository(
            hiddenVideoDao = FailingInsertHiddenVideoDao,
            mediaOperationsService = mediaOperations,
            context = context,
        )

        try {
            val result = runCatching {
                repository.hideVideos(
                    listOf(
                        Video.sample.copy(
                            path = sourceFile.absolutePath,
                            parentPath = sourceFile.parent.orEmpty(),
                            uriString = sourceFile.toUri().toString(),
                            nameWithExtension = fileName,
                        ),
                    ),
                )
            }

            assertTrue("The repository must handle a rejected vault record", result.isSuccess)
            assertEquals("The video must not be moved before its vault record exists", 0, mediaOperations.moveCalls)
            assertTrue("The original video must remain in place", sourceFile.exists())
            assertFalse("A failed hide must not leave a private vault copy", vaultFile.exists())
        } finally {
            sourceFile.delete()
            vaultFile.delete()
        }
    }

    @Test
    fun hideVideosUsesDistinctVaultFilesForMatchingSourceNames() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testRoot = File(
            requireNotNull(context.getExternalFilesDir(null)),
            "issue-1828-${UUID.randomUUID()}",
        )
        val firstSource = File(File(testRoot, "first").apply { mkdirs() }, "duplicate.mp4")
            .apply { writeText("first video") }
        val secondSource = File(File(testRoot, "second").apply { mkdirs() }, "duplicate.mp4")
            .apply { writeText("second video") }
        val dao = RecordingHiddenVideoDao()
        val repository = LocalVaultRepository(
            hiddenVideoDao = dao,
            mediaOperationsService = MovingMediaOperationsService(),
            context = context,
        )

        try {
            repository.hideVideos(
                listOf(
                    videoFor(firstSource),
                    videoFor(secondSource),
                ),
            )

            val vaultFiles = dao.entities.values.map { File(it.vaultPath) }
            assertEquals(2, vaultFiles.size)
            assertNotEquals(vaultFiles[0].absolutePath, vaultFiles[1].absolutePath)
            assertEquals(setOf("first video", "second video"), vaultFiles.map { it.readText() }.toSet())
            assertFalse(firstSource.exists())
            assertFalse(secondSource.exists())
        } finally {
            dao.entities.values.forEach { File(it.vaultPath).delete() }
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun hideVideosRemovesReservationAndKeepsSourceWhenMoveFails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceFile = File(context.cacheDir, "issue-1828-${UUID.randomUUID()}.mp4")
            .apply { writeText("original video") }
        val dao = RecordingHiddenVideoDao()
        val repository = LocalVaultRepository(
            hiddenVideoDao = dao,
            mediaOperationsService = MovingMediaOperationsService(moveSucceeds = false),
            context = context,
        )

        try {
            repository.hideVideos(listOf(videoFor(sourceFile)))

            assertTrue("A failed move must preserve the source", sourceFile.exists())
            assertTrue("A failed move must roll back its reserved row", dao.entities.isEmpty())
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun hideVideosCleansCommittedReservationWhenInsertIsCancelled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceFile = File(context.cacheDir, "issue-1828-${UUID.randomUUID()}.mp4")
            .apply { writeText("original video") }
        val dao = CommitThenCancelHiddenVideoDao()
        val mediaOperations = MovingMediaOperationsService()
        val repository = LocalVaultRepository(dao, mediaOperations, context)

        try {
            val result = runCatching { repository.hideVideos(listOf(videoFor(sourceFile))) }

            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue("Cancellation before moving must preserve the source", sourceFile.exists())
            assertTrue("A row committed before cancellation delivery must be cleaned up", dao.entities.isEmpty())
            assertEquals(0, mediaOperations.moveCalls)
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun hideVideosCleansReservationAndKeepsSourceWhenMoveIsCancelled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceFile = File(context.cacheDir, "issue-1828-${UUID.randomUUID()}.mp4")
            .apply { writeText("original video") }
        val dao = RecordingHiddenVideoDao()
        val repository = LocalVaultRepository(
            hiddenVideoDao = dao,
            mediaOperationsService = MovingMediaOperationsService(moveException = CancellationException("cancelled")),
            context = context,
        )

        try {
            val result = runCatching { repository.hideVideos(listOf(videoFor(sourceFile))) }

            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue("A cancelled move must preserve the source", sourceFile.exists())
            assertTrue("A cancelled move must clean up its reservation", dao.entities.isEmpty())
        } finally {
            sourceFile.delete()
        }
    }

    private fun videoFor(file: File): Video = Video.sample.copy(
        path = file.absolutePath,
        parentPath = file.parent.orEmpty(),
        uriString = file.toUri().toString(),
        nameWithExtension = file.name,
    )
}

private data object FailingInsertHiddenVideoDao : HiddenVideoDao {
    override suspend fun insert(hiddenVideo: HiddenVideoEntity): Long {
        error("UNIQUE constraint failed: hidden_video.vault_path")
    }

    override fun getAll(): Flow<List<HiddenVideoEntity>> = flowOf(emptyList())

    override suspend fun getById(id: Long): HiddenVideoEntity? = null

    override suspend fun deleteByIds(ids: List<Long>) = Unit

    override suspend fun deleteByVaultPaths(vaultPaths: List<String>) = Unit
}

private class RecordingHiddenVideoDao : HiddenVideoDao {
    val entities = linkedMapOf<Long, HiddenVideoEntity>()
    private var nextId = 1L

    override suspend fun insert(hiddenVideo: HiddenVideoEntity): Long {
        val id = nextId++
        entities[id] = hiddenVideo.copy(id = id)
        return id
    }

    override fun getAll(): Flow<List<HiddenVideoEntity>> = flowOf(entities.values.toList())

    override suspend fun getById(id: Long): HiddenVideoEntity? = entities[id]

    override suspend fun deleteByIds(ids: List<Long>) {
        ids.forEach(entities::remove)
    }

    override suspend fun deleteByVaultPaths(vaultPaths: List<String>) {
        entities.entries.removeAll { it.value.vaultPath in vaultPaths }
    }
}

private class CommitThenCancelHiddenVideoDao : HiddenVideoDao {
    val entities = linkedMapOf<Long, HiddenVideoEntity>()

    override suspend fun insert(hiddenVideo: HiddenVideoEntity): Long {
        entities[1L] = hiddenVideo.copy(id = 1L)
        throw CancellationException("cancelled after commit")
    }

    override fun getAll(): Flow<List<HiddenVideoEntity>> = flowOf(entities.values.toList())

    override suspend fun getById(id: Long): HiddenVideoEntity? = entities[id]

    override suspend fun deleteByIds(ids: List<Long>) {
        ids.forEach(entities::remove)
    }

    override suspend fun deleteByVaultPaths(vaultPaths: List<String>) {
        entities.entries.removeAll { it.value.vaultPath in vaultPaths }
    }
}

private class MovingMediaOperationsService(
    private val moveSucceeds: Boolean = true,
    private val moveException: Exception? = null,
) : MediaOperationsService {
    var moveCalls: Int = 0
        private set

    override fun initialize(activity: ComponentActivity) = Unit

    override suspend fun deleteMedia(uris: List<Uri>): Boolean = true

    override suspend fun renameMedia(uri: Uri, to: String): Boolean = false

    override suspend fun shareMedia(uris: List<Uri>) = Unit

    override suspend fun moveMedia(targets: Map<Uri, File>): Map<Uri, File?> {
        moveCalls++
        moveException?.let { throw it }
        return targets.mapValues { (uri, destination) ->
            if (!moveSucceeds) return@mapValues null
            val source = File(requireNotNull(uri.path))
            destination.parentFile?.mkdirs()
            if (source.renameTo(destination)) destination else null
        }
    }

    override fun transferMedia(
        uris: List<Uri>,
        folderUri: Uri,
        mode: TransferMode,
    ): Flow<TransferEvent> = emptyFlow()
}
