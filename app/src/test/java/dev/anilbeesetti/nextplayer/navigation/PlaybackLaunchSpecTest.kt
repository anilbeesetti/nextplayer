package dev.anilbeesetti.nextplayer.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLaunchSpecTest {
    @Test
    fun oneItemPlaylistLaunchIncludesExplicitPlaylistExtra() {
        val uri = "content://media/external/video/media/42"

        val spec = playbackLaunchSpec(
            items = listOf(uri),
            forcePlaylistExtra = true,
        )

        assertEquals(listOf(uri), spec.playlistExtra)
    }

    @Test
    fun ordinaryOneItemMediaLaunchKeepsPlaylistExtraAbsent() {
        val uri = "content://media/external/video/media/42"

        val spec = playbackLaunchSpec(items = listOf(uri))

        assertNull(spec.playlistExtra)
        assertFalse(spec.grantReadPermission)
    }

    @Test
    fun multiItemLaunchKeepsPlaylistExtraAndSelectedItem() {
        val items = listOf("content://video/first", "content://video/second")

        val spec = playbackLaunchSpec(
            items = items,
            startItem = items[1],
        )

        assertEquals(items, spec.playlistExtra)
        assertEquals(items[1], spec.startItem)
    }

    @Test
    fun grantedLaunchKeepsReadPermissionEnabled() {
        val spec = playbackLaunchSpec(
            items = listOf("content://vault/video/42"),
            grantReadPermission = true,
        )

        assertTrue(spec.grantReadPermission)
    }
}
