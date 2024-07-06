package dev.anilbeesetti.nextplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.anilbeesetti.nextplayer.core.database.MediaDatabase
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MediumDaoTest {

    private lateinit var mediumDao: MediumDao
    private lateinit var db: MediaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(
            context,
            MediaDatabase::class.java,
        ).build()
        mediumDao = db.mediumDao()
    }

    /**
     * Test to check if the [MediumDao.upsert] method inserts a [MediumEntity] into the database.
     */
    @Test
    fun mediumDao_inserts_mediumEntity() = runTest {
        val mediumEntity = sampleData[0]
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get(sampleData[0].uriString)

        assert(result == mediumEntity)
    }

    /**
     * Test to check if the [MediumDao.upsert] method updates a [MediumEntity] in the database.
     */
    @Test
    fun mediumDao_updates_mediumEntity() = runTest {
        val mediumEntity = sampleData[0]
        mediumDao.upsert(mediumEntity)

        val updatedMediumEntity = sampleData[0].copy(name = "Something")
        mediumDao.upsert(updatedMediumEntity)

        val result = mediumDao.get(sampleData[0].uriString)

        assert(result == updatedMediumEntity)
    }

    /**
     * Test to check if the [MediumDao.get] method returns the [MediumEntity] from the database.
     */
    @Test
    fun mediumDao_gets_mediumEntity_from_uri() = runTest {
        val mediumEntity = sampleData[0]
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get(sampleData[0].uriString)

        assert(result == mediumEntity)
    }

    /**
     * Test to check if the [MediumDao.get] method returns null if the path does not exist in the database.
     */
    @Test
    fun mediumDao_gets_null_if_uri_does_not_exist_in_database() = runTest {
        val mediumEntity = sampleData[0]
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get("uri1")

        assert(result == null)
    }

    @Test
    fun mediumDao_upsert_all_from_database() = runTest {
        val mediumEntities = sampleData

        mediumDao.upsertAll(mediumEntities)

        val updatedMediumEntities = mediumEntities.map { it.copy(subtitleTrackIndex = 8) }

        mediumDao.upsertAll(updatedMediumEntities)

        val result = mediumDao.getAll().first()

        assert(result == updatedMediumEntities)
    }

    @Test
    fun mediumDao_deletes_from_database() = runTest {
        val mediumEntities = sampleData

        mediumDao.upsertAll(mediumEntities)

        val toBeDeletedMediumEntities = mediumEntities.filterIndexed { index, _ -> index % 2 == 0 }
        val remainingMediumEntities = mediumEntities.filterNot { it in toBeDeletedMediumEntities }

        mediumDao.delete(toBeDeletedMediumEntities.map { it.uriString })

        val result = mediumDao.getAll().first()

        assert(result == remainingMediumEntities)
    }
}

val medium1 = MediumEntity(
    path = "/storage/emulated/0/media/video1.mp4",
    name = "video1.mp4",
    uriString = "content://media/external/video/media/1234",
    parentPath = "/storage/emulated/0/media",
    modified = System.currentTimeMillis(),
    size = 1024,
    width = 1920,
    height = 1080,
    duration = 60000,
    playbackPosition = 0,
    audioTrackIndex = 1,
    subtitleTrackIndex = null,
    playbackSpeed = 1f,
    mediaStoreId = 1234,
)

val medium2 = MediumEntity(
    path = "/storage/emulated/0/media/image1.jpg",
    name = "image1.jpg",
    uriString = "content://media/external/images/media/5678",
    parentPath = "/storage/emulated/0/media",
    modified = System.currentTimeMillis(),
    size = 512,
    width = 1280,
    height = 720,
    duration = 0,
    playbackPosition = 0,
    audioTrackIndex = null,
    subtitleTrackIndex = null,
    playbackSpeed = 1f,
    mediaStoreId = 5678,
)

val medium3 = MediumEntity(
    path = "/storage/emulated/0/media/song1.mp3",
    name = "song1.mp3",
    uriString = "content://media/external/audio/media/7890",
    parentPath = "/storage/emulated/0/media",
    modified = System.currentTimeMillis(),
    size = 4096,
    width = 0,
    height = 0,
    duration = 180000,
    playbackPosition = 0,
    audioTrackIndex = 0,
    subtitleTrackIndex = null,
    playbackSpeed = 1f,
    mediaStoreId = 7890,
)

val medium4 = MediumEntity(
    path = "/storage/emulated/0/media/image2.png",
    name = "image2.png",
    uriString = "content://media/external/images/media/2468",
    parentPath = "/storage/emulated/0/media",
    modified = System.currentTimeMillis(),
    size = 2048,
    width = 1920,
    height = 1080,
    duration = 0,
    playbackPosition = 0,
    audioTrackIndex = null,
    subtitleTrackIndex = null,
    playbackSpeed = 1f,
    mediaStoreId = 2468,
)

private val sampleData = listOf(medium1, medium2, medium3, medium4)
