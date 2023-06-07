package dev.anilbeesetti.nextplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.anilbeesetti.nextplayer.core.database.MediaDatabase
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediumDaoTest {

    private lateinit var mediumDao: MediumDao
    private lateinit var db: MediaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(
            context,
            MediaDatabase::class.java
        ).build()
        mediumDao = db.mediumDao()
    }

    /**
     * Test to check if the [MediumDao.upsert] method inserts a [MediumEntity] into the database.
     */
    @Test
    fun mediumDao_inserts_mediumEntity() = runTest {
        val mediumEntity = mediumSample1
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get(mediumSample1.path)

        assert(result == mediumEntity)
    }

    /**
     * Test to check if the [MediumDao.upsert] method updates a [MediumEntity] in the database.
     */
    @Test
    fun mediumDao_updates_mediumEntity() = runTest {
        val mediumEntity = mediumSample1
        mediumDao.upsert(mediumEntity)

        val updatedMediumEntity = mediumSample1.copy(name = "Something")
        mediumDao.upsert(updatedMediumEntity)

        val result = mediumDao.get(mediumSample1.path)

        assert(result == updatedMediumEntity)
    }

    /**
     * Test to check if the [MediumDao.get] method returns the [MediumEntity] from the database.
     */
    @Test
    fun mediumDao_gets_mediumEntity_from_path() = runTest {
        val mediumEntity = mediumSample1
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get(mediumSample1.path)

        assert(result == mediumEntity)
    }

    /**
     * Test to check if the [MediumDao.get] method returns null if the path does not exist in the database.
     */
    @Test
    fun mediumDao_gets_null_if_path_does_not_exist_in_database() = runTest {
        val mediumEntity = mediumSample1
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get("path1")

        assert(result == null)
    }
}


val mediumSample1 = MediumEntity(
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
    mediaStoreId = 1234
)


val mediumSample2 = MediumEntity(
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
    mediaStoreId = 5678
)

