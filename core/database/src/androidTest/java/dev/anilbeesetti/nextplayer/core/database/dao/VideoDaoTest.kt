package dev.anilbeesetti.nextplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.anilbeesetti.nextplayer.core.database.MediaDatabase
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoDaoTest {

    private lateinit var videoDao: VideoDao
    private lateinit var db: MediaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(
            context,
            MediaDatabase::class.java
        ).build()
        videoDao = db.videoDao()
    }

    @Test
    fun videoDao_inserts_videoEntity() = runTest {
        val videoEntity = VideoEntity(
            path = "path",
            playbackPosition = 0
        )
        videoDao.upsert(videoEntity)

        val result = videoDao.get("path")

        assert(result == videoEntity)
    }

    @Test
    fun videoDao_updates_videoEntity() = runTest {
        val videoEntity = VideoEntity(
            path = "path",
            playbackPosition = 0
        )
        videoDao.upsert(videoEntity)

        val updatedVideoEntity = VideoEntity(
            path = "path",
            playbackPosition = 100
        )
        videoDao.upsert(updatedVideoEntity)

        val result = videoDao.get("path")

        assert(result == updatedVideoEntity)
    }

    @Test
    fun videoDao_gets_videoEntity_from_path() = runTest {
        val videoEntity = VideoEntity(
            path = "path",
            playbackPosition = 0
        )
        videoDao.upsert(videoEntity)

        val result = videoDao.get("path")

        assert(result == videoEntity)
    }

    @Test
    fun videoDao_gets_null_if_path_does_not_exist_in_database() = runTest {
        val videoEntity = VideoEntity(
            path = "path",
            playbackPosition = 0
        )
        videoDao.upsert(videoEntity)

        val result = videoDao.get("path1")

        assert(result == null)
    }
}
