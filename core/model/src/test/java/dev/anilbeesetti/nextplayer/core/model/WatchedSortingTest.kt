package dev.anilbeesetti.nextplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchedSortingTest {
    private val videos = listOf(
        video(
            id = 1,
            name = "B.mp4",
            playbackPosition = -1,
        ),
        video(
            id = 2,
            name = "A.mp4",
            playbackPosition = null,
        ),
        video(
            id = 3,
            name = "C.mp4",
            playbackPosition = 500,
        ),
        video(
            id = 4,
            name = "D.mp4",
            playbackPosition = null,
        ),
    )

    @Test
    fun `watched ascending puts unwatched first and breaks ties by title`() {
        assertVideoOrder(
            Sort.Order.ASCENDING,
            2,
            4,
            3,
            1,
        )
    }

    @Test
    fun `watched descending puts fully watched first`() {
        assertVideoOrder(
            Sort.Order.DESCENDING,
            1,
            3,
            4,
            2,
        )
    }

    @Test
    fun `watched sort treats videos without a valid duration as unwatched`() {
        val noDuration = video(
            id = 5,
            name = "0.mp4",
            playbackPosition = 500,
        ).copy(duration = 0)
        val sorted = (videos + noDuration).sortedWith(
            Sort(
                by = Sort.By.WATCHED,
                order = Sort.Order.ASCENDING,
            ).videoComparator(),
        )

        assertEquals(
            listOf<Long>(
                5,
                2,
                4,
                3,
                1,
            ),
            sorted.map { it.id },
        )
    }

    @Test
    fun `watched sort falls back to name order for folders`() {
        val folders = listOf(
            Folder(
                name = "Season 10",
                path = "/Season 10",
                dateModified = 1,
            ),
            Folder(
                name = "Season 2",
                path = "/Season 2",
                dateModified = 2,
            ),
        )

        Sort.Order.entries.forEach { order ->
            assertEquals(
                folders.sortedWith(
                    Sort(
                        by = Sort.By.TITLE,
                        order = order,
                    ).folderComparator(),
                ),
                folders.sortedWith(
                    Sort(
                        by = Sort.By.WATCHED,
                        order = order,
                    ).folderComparator(),
                ),
            )
        }
    }

    private fun assertVideoOrder(order: Sort.Order, vararg ids: Long) {
        assertEquals(
            ids.toList(),
            videos.sortedWith(
                Sort(
                    by = Sort.By.WATCHED,
                    order = order,
                ).videoComparator(),
            ).map { it.id },
        )
    }

    private fun video(id: Long, name: String, playbackPosition: Long?) = Video(
        id = id,
        path = "/videos/$name",
        duration = 1000,
        uriString = "content://media/external/video/media/$id",
        nameWithExtension = name,
        width = 1920,
        height = 1080,
        size = 100,
        playbackPosition = playbackPosition,
    )
}
