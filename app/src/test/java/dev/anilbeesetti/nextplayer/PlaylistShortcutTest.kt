package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutManager
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlaylistShortcutTest {
    private val context = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val playlist = PlaylistSummary(42, "Daily rituals", PlaylistType.LOCAL, 0, null)

    @Test
    fun `shortcut targets the playlist with its name and an explicit activity`() {
        val shortcut = PlaylistShortcut.create(context, playlist)

        assertEquals("playlist_42", shortcut.id)
        assertEquals(playlist.name, shortcut.shortLabel)
        assertEquals(MainActivity::class.java.name, shortcut.intent.component?.className)
        assertEquals(42L, PlaylistShortcut.playlistId(shortcut.intent))
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            shortcut.intent.flags,
        )
    }

    @Test
    fun `pin request supplies the playlist to a supported launcher`() {
        val manager = context.getSystemService(ShortcutManager::class.java)
        shadowOf(manager).setIsRequestPinShortcutSupported(false)
        assertFalse(PlaylistShortcut.isSupported(context))
        shadowOf(manager).setIsRequestPinShortcutSupported(true)

        assertTrue(PlaylistShortcut.isSupported(context))
        assertTrue(PlaylistShortcut.requestPin(context, playlist))
        val pinned = manager.pinnedShortcuts.single()
        assertEquals(playlist.name, pinned.shortLabel)
        assertEquals(playlist.id, PlaylistShortcut.playlistId(checkNotNull(pinned.intent)))
    }

    @Test
    fun `shortcut identity survives rename and differs between playlists`() {
        val original = PlaylistShortcut.create(context, playlist)
        val renamed = PlaylistShortcut.create(context, playlist.copy(name = "Renamed"))
        val another = PlaylistShortcut.create(context, playlist.copy(id = 43))

        assertEquals(original.id, renamed.id)
        assertEquals("Renamed", renamed.shortLabel)
        assertEquals("playlist_43", another.id)
    }

    @Test
    fun `regular launch and invalid shortcut IDs do not open a playlist`() {
        val intent = PlaylistShortcut.create(context, playlist).intent

        assertNull(PlaylistShortcut.playlistId(Intent(Intent.ACTION_MAIN)))
        assertNull(PlaylistShortcut.playlistId(Intent(intent).setAction(Intent.ACTION_VIEW)))
        assertNull(PlaylistShortcut.playlistId(Intent(intent).apply { removeExtra("playlist_id") }))
        assertNull(PlaylistShortcut.playlistId(Intent(intent).putExtra("playlist_id", 0L)))
        assertNull(PlaylistShortcut.playlistId(Intent(intent).putExtra("playlist_id", -1L)))
    }
}
