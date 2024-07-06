package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSortedVideosUseCaseTest {

    private val mediaRepository = FakeMediaRepository()
    private val preferencesRepository = FakePreferencesRepository()

    val getSortedVideosUseCase = GetSortedVideosUseCase(mediaRepository, preferencesRepository)

    @Test
    fun testGetSortedVideosUseCase_whenSortByTitleAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.TITLE, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.displayName.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByTitleDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.TITLE, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.displayName.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByDurationAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.LENGTH, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.duration })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByDurationDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.LENGTH, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.duration })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByPathAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.PATH, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.path.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByPathDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.PATH, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.path.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortBySizeAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.SIZE, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.size })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortBySizeDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.SIZE, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.size })
    }
}

/**
 * Sorted video items by title in ascending order and duration in ascending order.
 */
val testVideoItems = listOf(
    Video(
        id = 1,
        displayName = "A",
        duration = 1000,
        uriString = "content://media/external/video/media/1",
        height = 1920,
        nameWithExtension = "A.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/A.mp4",
        size = 1000,
    ),
    Video(
        id = 2,
        displayName = "B",
        duration = 2000,
        uriString = "content://media/external/video/media/2",
        height = 1930,
        nameWithExtension = "B.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/B.mp4",
        size = 2000,
    ),
    Video(
        id = 3,
        displayName = "C",
        duration = 3000,
        uriString = "content://media/external/video/media/3",
        height = 1940,
        nameWithExtension = "C.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/C.mp4",
        size = 3000,
    ),
    Video(
        id = 4,
        displayName = "D",
        duration = 4000,
        uriString = "content://media/external/video/media/4",
        height = 1950,
        nameWithExtension = "D.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/D.mp4",
        size = 4000,
    ),
    Video(
        id = 5,
        displayName = "E",
        duration = 5000,
        uriString = "content://media/external/video/media/5",
        height = 1960,
        nameWithExtension = "E.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/E.mp4",
        size = 5000,
    ),
    Video(
        id = 6,
        displayName = "F",
        duration = 6000,
        uriString = "content://media/external/video/media/6",
        height = 1970,
        nameWithExtension = "F.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/F.mp4",
        size = 6000,
    ),
    Video(
        id = 7,
        displayName = "G",
        duration = 7000,
        uriString = "content://media/external/video/media/7",
        height = 1980,
        nameWithExtension = "G.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/G.mp4",
        size = 7000,
    ),
    Video(
        id = 8,
        displayName = "H",
        duration = 8000,
        uriString = "content://media/external/video/media/8",
        height = 1990,
        nameWithExtension = "H.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/H.mp4",
        size = 8000,
    ),
    Video(
        id = 9,
        displayName = "I",
        duration = 9000,
        uriString = "content://media/external/video/media/9",
        height = 2160,
        nameWithExtension = "I.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/I.mp4",
        size = 9000,
    ),
    Video(
        id = 10,
        displayName = "J",
        duration = 10000,
        uriString = "content://media/external/video/media/10",
        height = 2170,
        nameWithExtension = "J.mp4",
        width = 1080,
        path = "/storage/emulated/0/DCIM/Camera/J.mp4",
        size = 10000,
    ),
)
