package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSortedFoldersUseCaseTest {

    private val mediaRepository = FakeMediaRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val useCase = GetSortedFoldersUseCase(
        mediaRepository,
        preferencesRepository,
        Dispatchers.Unconfined,
    )
    private val now = System.currentTimeMillis()
    private val oneDayMillis = 24 * 60 * 60 * 1000L

    @Test
    fun newVideoCountsOnlyIncludeDirectVideosWithinThreshold() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(newVideoThresholdDays = 3)
        }
        mediaRepository.videos.addAll(
            listOf(
                video(id = 1, parentPath = "/storage/emulated/0/Movies", addedDaysAgo = 2),
                video(id = 2, parentPath = "/storage/emulated/0/Movies", addedDaysAgo = 5),
                video(id = 3, parentPath = "/storage/emulated/0/Movies/Action", addedDaysAgo = 1),
            ),
        )

        val folders = useCase().first().associateBy { it.path }

        assertEquals(2, folders.getValue("/storage/emulated/0/Movies").videosCount)
        assertEquals(1, folders.getValue("/storage/emulated/0/Movies").newVideosCount)
        assertEquals(1, folders.getValue("/storage/emulated/0/Movies/Action").newVideosCount)
    }

    private fun video(id: Long, parentPath: String, addedDaysAgo: Long) = Video(
        id = id,
        path = "$parentPath/video_$id.mp4",
        parentPath = parentPath,
        duration = 1_000,
        uriString = "content://media/external/video/media/$id",
        nameWithExtension = "video_$id.mp4",
        width = 1920,
        height = 1080,
        size = 1_000,
        dateAdded = (now - addedDaysAgo * oneDayMillis) / 1000L,
    )
}
