package dev.anilbeesetti.nextplayer.navigation

import android.app.Activity
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
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
}
