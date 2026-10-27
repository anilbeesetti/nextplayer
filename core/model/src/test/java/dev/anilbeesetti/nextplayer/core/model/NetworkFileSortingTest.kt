package dev.anilbeesetti.nextplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkFileSortingTest {
    private val files = listOf(
        NetworkFile(
            name = "Episode 10.mp4",
            path = "10",
            isDirectory = false,
            size = 10,
            modified = 300,
        ),
        NetworkFile(
            name = "Season 10",
            path = "folder10",
            isDirectory = true,
            modified = 200,
        ),
        NetworkFile(
            name = "episode 2.mp4",
            path = "2",
            isDirectory = false,
            size = 30,
            modified = 100,
        ),
        NetworkFile(
            name = "Season 2",
            path = "folder2",
            isDirectory = true,
            modified = null,
        ),
        NetworkFile(
            name = "Episode 1.mp4",
            path = "1",
            isDirectory = false,
            size = 20,
            modified = null,
        ),
    )

    @Test
    fun `title sort uses natural case insensitive order and keeps folders first`() {
        assertOrder(
            Sort.By.TITLE,
            Sort.Order.ASCENDING,
            "folder2",
            "folder10",
            "1",
            "2",
            "10",
        )
        assertOrder(
            Sort.By.TITLE,
            Sort.Order.DESCENDING,
            "folder10",
            "folder2",
            "10",
            "2",
            "1",
        )
    }

    @Test
    fun `date sort handles missing dates and keeps folders first in both directions`() {
        assertOrder(
            Sort.By.DATE,
            Sort.Order.ASCENDING,
            "folder2",
            "folder10",
            "1",
            "2",
            "10",
        )
        assertOrder(
            Sort.By.DATE,
            Sort.Order.DESCENDING,
            "folder10",
            "folder2",
            "10",
            "2",
            "1",
        )
    }

    @Test
    fun `size sort keeps directories first and uses titles to break equal sizes`() {
        assertOrder(
            Sort.By.SIZE,
            Sort.Order.ASCENDING,
            "folder2",
            "folder10",
            "10",
            "1",
            "2",
        )
        assertOrder(
            Sort.By.SIZE,
            Sort.Order.DESCENDING,
            "folder10",
            "folder2",
            "2",
            "1",
            "10",
        )
    }

    private fun assertOrder(by: Sort.By, order: Sort.Order, vararg paths: String) {
        assertEquals(
            paths.toList(),
            files.sortedWith(
                Sort(
                    by = by,
                    order = order,
                ).networkFileComparator(),
            ).map { it.path },
        )
    }
}
