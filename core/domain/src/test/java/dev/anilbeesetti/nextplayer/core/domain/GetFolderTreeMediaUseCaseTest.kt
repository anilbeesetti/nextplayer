package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetFolderTreeMediaUseCaseTest {

    private val mediaRepository = FakeMediaRepository()
    private val preferencesRepository = FakePreferencesRepository()

    private val getFolderTreeMediaUseCase = GetFolderTreeMediaUseCase(
        mediaRepository,
        preferencesRepository,
        Dispatchers.Unconfined,
    )

    private val now = System.currentTimeMillis()
    private val oneDayMillis = 24 * 60 * 60 * 1000L
    private val folderPath = "/storage/emulated/0/Movies"

    private fun video(
        id: Long,
        addedDaysAgo: Long,
        watched: Boolean,
    ) = Video(
        id = id,
        path = "$folderPath/video_$id.mp4",
        parentPath = folderPath,
        duration = 1000,
        uriString = "content://media/external/video/media/$id",
        nameWithExtension = "video_$id.mp4",
        width = 1920,
        height = 1080,
        size = 1000,
        dateModified = (now - addedDaysAgo * oneDayMillis) / 1000L,
        lastPlayedAt = if (watched) Date(now) else null,
    )

    @Test
    fun folderNewVideosCount_onlyCountsUnwatchedVideosAddedWithinLast7Days() = runTest {
        mediaRepository.videos.addAll(
            listOf(
                video(id = 1, addedDaysAgo = 1, watched = false), // new
                video(id = 2, addedDaysAgo = 6, watched = false), // new
                video(id = 3, addedDaysAgo = 8, watched = false), // too old, not new
                video(id = 4, addedDaysAgo = 1, watched = true), // watched, not new
            ),
        )

        val media = getFolderTreeMediaUseCase(folderPath = null).first()

        val moviesFolder = media.folders.first { it.path == folderPath }
        assertEquals(4, moviesFolder.videosCount)
        assertEquals(2, moviesFolder.newVideosCount)
    }

    @Test
    fun folderNewVideosCount_isZeroWhenNoVideosAreNew() = runTest {
        mediaRepository.videos.addAll(
            listOf(
                video(id = 1, addedDaysAgo = 10, watched = false),
                video(id = 2, addedDaysAgo = 1, watched = true),
            ),
        )

        val media = getFolderTreeMediaUseCase(folderPath = null).first()

        val moviesFolder = media.folders.first { it.path == folderPath }
        assertEquals(0, moviesFolder.newVideosCount)
    }

    @Test
    fun folderNewVideosCount_excludesVideosFromExcludedFolders() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(excludeFolders = listOf(folderPath))
        }
        mediaRepository.videos.addAll(
            listOf(video(id = 1, addedDaysAgo = 1, watched = false)),
        )

        val media = getFolderTreeMediaUseCase(folderPath = null).first()

        assertEquals(emptyList<Folder>(), media.folders.filter { it.path == folderPath })
    }
}
