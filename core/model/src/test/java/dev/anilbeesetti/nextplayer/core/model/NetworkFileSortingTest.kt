package dev.anilbeesetti.nextplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkFileSortingTest {
    private val files = listOf(
        NetworkFile("Episode 10.mp4", "10", false, size = 10, modified = 300),
        NetworkFile("Season 10", "folder10", true, modified = 200),
        NetworkFile("episode 2.mp4", "2", false, size = 30, modified = 100),
        NetworkFile("Season 2", "folder2", true, modified = null),
        NetworkFile("Episode 1.mp4", "1", false, size = 20, modified = null),
    )

    @Test
    fun `title sort uses natural case insensitive order and keeps folders first`() {
        assertOrder(Sort.By.TITLE, Sort.Order.ASCENDING, "folder2", "folder10", "1", "2", "10")
        assertOrder(Sort.By.TITLE, Sort.Order.DESCENDING, "folder10", "folder2", "10", "2", "1")
    }

    @Test
    fun `date sort handles missing dates and keeps folders first in both directions`() {
        assertOrder(Sort.By.DATE, Sort.Order.ASCENDING, "folder2", "folder10", "1", "2", "10")
        assertOrder(Sort.By.DATE, Sort.Order.DESCENDING, "folder10", "folder2", "10", "2", "1")
    }

    @Test
    fun `size sort keeps directories first and uses titles to break equal sizes`() {
        assertOrder(Sort.By.SIZE, Sort.Order.ASCENDING, "folder2", "folder10", "10", "1", "2")
        assertOrder(Sort.By.SIZE, Sort.Order.DESCENDING, "folder10", "folder2", "2", "1", "10")
    }

    private fun assertOrder(by: Sort.By, order: Sort.Order, vararg paths: String) {
        assertEquals(paths.toList(), files.sortedWith(Sort(by, order).networkFileComparator()).map { it.path })
    }
}
