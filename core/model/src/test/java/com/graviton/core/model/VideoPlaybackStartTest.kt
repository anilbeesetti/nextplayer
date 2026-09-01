package com.graviton.core.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPlaybackStartTest {

    @Test
    fun playbackStart_usesMostRecentlyPlayedVideo() {
        val first = video("one", lastPlayedAt = Date(100))
        val second = video("two", lastPlayedAt = Date(300))
        val third = video("three", lastPlayedAt = Date(200))
        assertEquals("content://media/two", listOf(first, second, third).playbackStart()?.uriString)
    }

    @Test
    fun playbackStart_fallsBackToFirstPlayableItem() {
        val first = video("one")
        val second = video("two")
        assertEquals("content://media/one", listOf(first, second).playbackStart()?.uriString)
    }

    @Test
    fun playbackStart_isNullWhenFolderIsEmpty() {
        assertNull(emptyList<Video>().playbackStart())
    }

    private fun video(id: String, lastPlayedAt: Date? = null): Video = Video(
        id = id.hashCode().toLong(),
        path = "/Movies/$id.mp4",
        parentPath = "/Movies",
        duration = 60_000,
        uriString = "content://media/$id",
        nameWithExtension = "$id.mp4",
        width = 1920,
        height = 1080,
        size = 1_000,
        lastPlayedAt = lastPlayedAt,
    )
}
