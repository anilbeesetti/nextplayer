package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import dev.anilbeesetti.nextplayer.core.database.MediaDatabase
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.datastore.datasource.AppPreferencesDataSource
import dev.anilbeesetti.nextplayer.core.datastore.serializer.ApplicationPreferencesSerializer
import dev.anilbeesetti.nextplayer.core.media.services.MediaFolder
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalMediaRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        MediaDatabase::class.java,
    ).build()
    private val dao = database.mediumStateDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `persisted pause skips watch timestamps while preserving playback state and resumes tracking`() = runTest {
        val file = temporaryFolder.newFile().apply { writeText("""{"isHistoryPaused":true}""") }
        val preferences = AppPreferencesDataSource(
            DataStoreFactory.create(
                serializer = ApplicationPreferencesSerializer,
                scope = backgroundScope,
                produceFile = { file },
            ),
        )
        dao.upsert(MediumStateEntity(uriString = "content://watched", lastPlayedTime = 100))
        val mediaService = object : MediaService {
            override fun observeFolders(folderPath: String?) = flowOf(emptyList<MediaFolder>())
            override fun observeVideos(folderPath: String?) = flowOf(emptyList<MediaVideo>())
            override fun observeTrashVideos() = observeVideos(null)
            override suspend fun fetchFolders(folderPath: String?) = emptyList<MediaFolder>()
            override suspend fun fetchVideos(folderPath: String?) = emptyList<MediaVideo>()
            override suspend fun findVideo(uri: Uri) = null
            override suspend fun findFolder(path: String) = null
        }
        val repository = LocalMediaRepository(dao, mediaService, preferences, RuntimeEnvironment.getApplication())

        repository.updateMediumLastPlayedTime("content://watched", 200, 1_000)
        repository.updateMediumLastPlayedTime("content://new", 200, 1_000)
        repository.updateMediumPosition("content://new", 400)
        assertEquals(100L, dao.get("content://watched")?.lastPlayedTime)
        assertNull(dao.get("content://new")?.lastPlayedTime)
        assertEquals(1_000L, dao.get("content://new")?.duration)
        assertEquals(400L, dao.get("content://new")?.playbackPosition)

        preferences.update { it.copy(isHistoryPaused = false) }
        repository.updateMediumLastPlayedTime("content://new", 300, null)
        assertEquals(300L, dao.get("content://new")?.lastPlayedTime)
        assertEquals(1_000L, dao.get("content://new")?.duration)

        preferences.update { it.copy(isHistoryPaused = true) }
        repository.clearPlaybackHistory()
        repository.updateMediumLastPlayedTime("content://new", 400, null)
        assertNull(dao.get("content://new")?.lastPlayedTime)
        assertNull(dao.get("content://watched")?.lastPlayedTime)
        assertEquals(400L, dao.get("content://new")?.playbackPosition)
    }

    @Test
    fun `concurrent playback state updates preserve each other's changes`() = runTest {
        val uri = "content://concurrent"
        coroutineScope {
            repeat(50) {
                launch(Dispatchers.IO) {
                    dao.update(uri) { it.copy(playbackPosition = it.playbackPosition + 1) }
                }
                launch(Dispatchers.IO) {
                    dao.update(uri) { it.copy(lastPlayedTime = (it.lastPlayedTime ?: 0) + 1) }
                }
            }
        }
        assertEquals(50L, dao.get(uri)?.playbackPosition)
        assertEquals(50L, dao.get(uri)?.lastPlayedTime)
    }
}
