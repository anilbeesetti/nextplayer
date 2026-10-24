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
import kotlinx.coroutines.flow.first
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
    fun `history respects privacy preferences and preserves playback state`() = runTest {
        val file = temporaryFolder.newFile().apply {
            writeText(text = """{"isHistoryPaused":true}""")
        }
        val preferences = AppPreferencesDataSource(
            DataStoreFactory.create(
                serializer = ApplicationPreferencesSerializer,
                scope = backgroundScope,
                produceFile = { file },
            ),
        )
        dao.upsert(
            mediumState = MediumStateEntity(
                uriString = "content://watched",
                lastPlayedTime = 100,
            ),
        )
        val mediaService = object : MediaService {
            override fun observeFolders(folderPath: String?) =
                flowOf(value = emptyList<MediaFolder>())
            override fun observeVideos(folderPath: String?) =
                flowOf(value = emptyList<MediaVideo>())
            override fun observeTrashVideos() = observeVideos(folderPath = null)
            override suspend fun fetchFolders(folderPath: String?) = emptyList<MediaFolder>()
            override suspend fun fetchVideos(folderPath: String?) = emptyList<MediaVideo>()
            override suspend fun findVideo(uri: Uri) = null
            override suspend fun findFolder(path: String) = null
        }
        val repository = LocalMediaRepository(
            mediumStateDao = dao,
            mediaService = mediaService,
            appPreferencesDataSource = preferences,
            RuntimeEnvironment.getApplication(),
        )
        val networkUri = "smb://example.com/films/movie.mkv?cid=1"
        val adHocUri = "smb://example.com/share/movie.mkv"
        val vaultUri =
            "content://${RuntimeEnvironment.getApplication().packageName}" +
                ".fileprovider/external_files_path/vault/movie.mkv"
        dao.upsert(
            mediumState = MediumStateEntity(
                uriString = vaultUri,
                lastPlayedTime = 90,
            ),
        )
        assertEquals(
            listOf(element = "content://watched"),
            repository.observePlaybackHistory().first().map { it.uriString },
        )

        repository.updateMediumLastPlayedTime(
            uri = "content://watched",
            lastPlayedTime = 200,
            duration = 1_000,
        )
        repository.updateMediumLastPlayedTime(
            uri = "content://new",
            lastPlayedTime = 200,
            duration = 1_000,
        )
        repository.updateMediumPosition(
            uri = "content://new",
            position = 400,
        )
        assertEquals(
            100L,
            dao.get(uri = "content://watched")?.lastPlayedTime,
        )
        assertNull(dao.get(uri = "content://new")?.lastPlayedTime)
        assertEquals(
            1_000L,
            dao.get(uri = "content://new")?.duration,
        )
        assertEquals(
            400L,
            dao.get(uri = "content://new")?.playbackPosition,
        )

        preferences.update { it.copy(isHistoryPaused = false) }
        repository.updateMediumLastPlayedTime(
            uri = "content://new",
            lastPlayedTime = 300,
            duration = null,
        )
        assertEquals(
            300L,
            dao.get(uri = "content://new")?.lastPlayedTime,
        )
        assertEquals(
            1_000L,
            dao.get(uri = "content://new")?.duration,
        )
        preferences.update { it.copy(includeNetworkWatchHistory = false) }
        repository.updateMediumLastPlayedTime(
            uri = networkUri,
            lastPlayedTime = 500,
            duration = 2_000,
        )
        assertNull(dao.get(uri = networkUri)?.lastPlayedTime)
        assertEquals(
            2_000L,
            dao.get(uri = networkUri)?.duration,
        )
        repository.updateMediumLastPlayedTime(
            uri = vaultUri,
            lastPlayedTime = 500,
            duration = 2_000,
        )
        repository.updateMediumLastPlayedTime(
            uri = adHocUri,
            lastPlayedTime = 500,
            duration = null,
        )
        assertEquals(
            500L,
            dao.get(uri = adHocUri)?.lastPlayedTime,
        )
        assertEquals(
            90L,
            dao.get(uri = vaultUri)?.lastPlayedTime,
        )
        preferences.update { it.copy(includeNetworkWatchHistory = true) }
        repository.updateMediumLastPlayedTime(
            uri = networkUri,
            lastPlayedTime = 600,
            duration = null,
        )
        assertEquals(
            600L,
            dao.get(uri = networkUri)?.lastPlayedTime,
        )

        preferences.update { it.copy(isHistoryPaused = true) }
        repository.clearPlaybackHistory()
        repository.updateMediumLastPlayedTime(
            uri = "content://new",
            lastPlayedTime = 400,
            duration = null,
        )
        assertNull(dao.get(uri = "content://new")?.lastPlayedTime)
        assertNull(dao.get(uri = "content://watched")?.lastPlayedTime)
        assertEquals(
            400L,
            dao.get(uri = "content://new")?.playbackPosition,
        )
    }

    @Test
    fun `concurrent playback state updates preserve each other's changes`() = runTest {
        val uri = "content://concurrent"
        coroutineScope {
            repeat(times = 50) {
                launch(Dispatchers.IO) {
                    dao.update(uri = uri) { it.copy(playbackPosition = it.playbackPosition + 1) }
                }
                launch(Dispatchers.IO) {
                    dao.update(uri = uri) { it.copy(lastPlayedTime = (it.lastPlayedTime ?: 0) + 1) }
                }
            }
        }
        assertEquals(
            50L,
            dao.get(uri = uri)?.playbackPosition,
        )
        assertEquals(
            50L,
            dao.get(uri = uri)?.lastPlayedTime,
        )
    }
}
