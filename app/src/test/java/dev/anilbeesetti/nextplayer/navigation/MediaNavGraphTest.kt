package dev.anilbeesetti.nextplayer.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.player.utils.PlaylistPlaybackContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MediaNavGraphTest {

    @Test
    fun `single item explicit playlist is included in playback intent`() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val uri = "content://media/external/video/media/1821".toUri()

        context.startPlayback(listOf(uri))

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(
            arrayListOf(uri),
            IntentCompat.getParcelableArrayListExtra(intent, PlayerApi.API_PLAYLIST, Uri::class.java),
        )
    }

    @Test
    fun `single video playback does not include explicit playlist`() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val uri = "content://media/external/video/media/1821".toUri()

        context.startPlayback(uri)

        val intent = shadowOf(context).nextStartedActivity
        assertFalse(intent.hasExtra(PlayerApi.API_PLAYLIST))
    }

    @Test
    fun `empty explicit playlist does not start playback`() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()

        context.startPlayback(emptyList())

        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun `explicit playlist starts at requested uri without changing queue order`() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val first = "content://media/external/video/media/1".toUri()
        val second = "content://media/external/video/media/2".toUri()

        context.startPlayback(listOf(first, second), startUri = second)

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(second, intent.data)
        assertEquals(
            arrayListOf(first, second),
            IntentCompat.getParcelableArrayListExtra(intent, PlayerApi.API_PLAYLIST, Uri::class.java),
        )
    }

    @Test
    fun `saved playlist playback sends only playlist id and selected uri`() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val selected = "https://example.com/live".toUri()

        context.startPlaylistPlayback(
            playlistId = 42,
            startUri = selected,
        )

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(selected, intent.data)
        assertEquals(
            42L,
            intent.getLongExtra(PlaylistPlaybackContract.EXTRA_PLAYLIST_ID, -1),
        )
        assertFalse(intent.hasExtra(PlayerApi.API_PLAYLIST))
    }
}
