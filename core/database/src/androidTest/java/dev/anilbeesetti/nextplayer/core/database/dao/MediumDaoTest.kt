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
        mediumDao = db.MediaDao()
    }

    /**
     * Test to check if the [MediumDao.upsert] method inserts a [MediumEntity] into the database.
     */
    @Test
    fun videoDao_inserts_videoEntity() = runTest {
        val mediumEntity = MediumEntity(
            path = "path",
            playbackPosition = 0
        )
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get("path")

        assert(result == mediumEntity)
    }

    /**
     * Test to check if the [MediumDao.upsert] method updates a [MediumEntity] in the database.
     */
    @Test
    fun videoDao_updates_videoEntity() = runTest {
        val mediumEntity = MediumEntity(
            path = "path",
            playbackPosition = 0
        )
        mediumDao.upsert(mediumEntity)

        val updatedMediumEntity = MediumEntity(
            path = "path",
            playbackPosition = 100
        )
        mediumDao.upsert(updatedMediumEntity)

        val result = mediumDao.get("path")

        assert(result == updatedMediumEntity)
    }

    /**
     * Test to check if the [MediumDao.get] method returns the [MediumEntity] from the database.
     */
    @Test
    fun videoDao_gets_videoEntity_from_path() = runTest {
        val mediumEntity = MediumEntity(
            path = "path",
            playbackPosition = 0
        )
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get("path")

        assert(result == mediumEntity)
    }

    /**
     * Test to check if the [MediumDao.get] method returns null if the path does not exist in the database.
     */
    @Test
    fun videoDao_gets_null_if_path_does_not_exist_in_database() = runTest {
        val mediumEntity = MediumEntity(
            path = "path",
            playbackPosition = 0
        )
        mediumDao.upsert(mediumEntity)

        val result = mediumDao.get("path1")

        assert(result == null)
    }
}
