package dev.anilbeesetti.nextplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.anilbeesetti.nextplayer.core.database.MediaDatabase
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryDaoTest {

    private lateinit var directoryDao: DirectoryDao
    private lateinit var db: MediaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(
            context,
            MediaDatabase::class.java
        ).build()
        directoryDao = db.directoryDao()
    }

    /**
     * Test to check if the [DirectoryDao.upsert] method inserts a [DirectoryEntity] into the database.
     */
    @Test
    fun directoryDao_inserts_directoryEntity() = runTest {
        val directoryEntity = sampleData[0]
        directoryDao.upsert(directoryEntity)

        val result = directoryDao.getAll().first()

        assert(result[0] == directoryEntity)
    }

    /**
     * Test to check if the [DirectoryDao.upsert] method updates a [DirectoryEntity] in the database.
     */
    @Test
    fun directoryDao_updates_directoryEntity() = runTest {
        val directoryEntity = sampleData[0]
        directoryDao.upsert(directoryEntity)

        val updatedDirectoryEntity = sampleData[0].copy(name = "Something")
        directoryDao.upsert(updatedDirectoryEntity)

        val result = directoryDao.getAll().first()

        assert(result[0] == updatedDirectoryEntity)
    }

    @Test
    fun directoryDao_upsert_all_from_database() = runTest {
        val directoryEntities = sampleData

        directoryDao.upsertAll(directoryEntities)

        val updatedDirectoryEntities = directoryEntities.map { it.copy(size = 5) }

        directoryDao.upsertAll(updatedDirectoryEntities)

        val result = directoryDao.getAll().first()

        assert(result == updatedDirectoryEntities)
    }

    @Test
    fun directoryDao_deletes_from_database() = runTest {
        val directoryEntities = sampleData

        directoryDao.upsertAll(directoryEntities)

        val toBeDeletedDirectoryEntities = directoryEntities.filterIndexed { index, _ -> index % 2 == 0 }
        val remainingDirectoryEntities = directoryEntities.filterNot { it in toBeDeletedDirectoryEntities }

        directoryDao.delete(toBeDeletedDirectoryEntities.map { it.path })

        val result = directoryDao.getAll().first()

        assert(result == remainingDirectoryEntities)
    }
}

val directory1 = DirectoryEntity(
    path = "/storage/emulated/0/media",
    name = "media",
    mediaCount = 10,
    modified = System.currentTimeMillis(),
    size = 1024
)

val directory2 = DirectoryEntity(
    path = "/storage/emulated/0/pictures",
    name = "pictures",
    mediaCount = 5,
    modified = System.currentTimeMillis(),
    size = 512
)

val directory3 = DirectoryEntity(
    path = "/storage/emulated/0/music",
    name = "music",
    mediaCount = 20,
    modified = System.currentTimeMillis(),
    size = 2048
)

val directory4 = DirectoryEntity(
    path = "/storage/emulated/0/videos",
    name = "videos",
    mediaCount = 8,
    modified = System.currentTimeMillis(),
    size = 1024
)

private val sampleData = listOf(directory1, directory2, directory3, directory4)
