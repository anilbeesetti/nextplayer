package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import androidx.datastore.core.DataStoreFactory
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.datastore.datasource.AppPreferencesDataSource
import dev.anilbeesetti.nextplayer.core.datastore.serializer.ApplicationPreferencesSerializer
import dev.anilbeesetti.nextplayer.core.media.services.MediaFolder
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
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
        val states = MutableStateFlow(listOf(MediumStateEntity(uriString = "content://watched", lastPlayedTime = 100)))
        val dao = object : MediumStateDao {
            override suspend fun upsert(mediumState: MediumStateEntity) {
                states.value = states.value.filterNot { it.uriString == mediumState.uriString } + mediumState
            }
            override suspend fun upsertAll(mediaStates: List<MediumStateEntity>) = mediaStates.forEach { upsert(it) }
            override suspend fun get(uri: String) = states.value.find { it.uriString == uri }
            override fun getAsFlow(uri: String) = states.map { values -> values.find { it.uriString == uri } }
            override fun getAll() = states
            override suspend fun clearPlaybackHistory() {
                states.value = states.value.map { it.copy(lastPlayedTime = null) }
            }
            override suspend fun delete(uris: List<String>) {
                states.value = states.value.filterNot { it.uriString in uris }
            }
        }
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
}
