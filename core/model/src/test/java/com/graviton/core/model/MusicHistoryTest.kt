package com.graviton.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicHistoryTest {

    @Test
    fun recordMusicPlay_putsNewestFirstAndDedupes() {
        val first = ApplicationPreferences().recordMusicPlay("content://one", "/Music/A")
        val second = first.recordMusicPlay("content://two", "/Music/B")
        val again = second.recordMusicPlay("content://one", "/Music/A")

        assertEquals(listOf("content://one", "content://two"), again.musicRecentlyPlayedUris)
        assertEquals("content://one", again.lastMusicUriForFolder("/Music/A"))
        assertEquals("content://two", again.lastMusicUriForFolder("/Music/B"))
    }

    @Test
    fun recordMusicPlay_ignoresBlankUri() {
        val prefs = ApplicationPreferences().recordMusicPlay("   ")
        assertEquals(emptyList<String>(), prefs.musicRecentlyPlayedUris)
    }

    @Test
    fun startIndexForFolderPlayback_usesLastPlayedWhenPresent() {
        val tracks = listOf("a", "b", "c")
        assertEquals(2, startIndexForFolderPlayback(tracks, "c"))
        assertEquals(0, startIndexForFolderPlayback(tracks, "missing"))
        assertEquals(0, startIndexForFolderPlayback(tracks, null))
        assertEquals(0, startIndexForFolderPlayback(emptyList(), "c"))
    }

    @Test
    fun lastMusicUriForFolder_isNullWhenUnknown() {
        assertNull(ApplicationPreferences().lastMusicUriForFolder("/none"))
    }
}
