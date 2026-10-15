package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlayerApiTest {
    private val video = Uri.parse("https://example.com/video.mp4")
    private val subtitle = Uri.parse("https://example.com/sub.srt")

    private fun api(intent: Intent): PlayerApi {
        val activity = Robolectric.buildActivity(PlayerActivity::class.java).get()
        activity.intent = intent
        return PlayerApi(activity)
    }

    @Test
    fun falseReturnResultDoesNotRequestCallback() {
        assertFalse(api(Intent().putExtra("return_result", false)).shouldReturnResult)
        assertTrue(api(Intent().putExtra("return_result", true)).shouldReturnResult)
    }

    @Test
    fun unrelatedExtrasAreNotAnApiRequest() {
        assertFalse(api(Intent().putExtra("unrelated", true)).isApiAccess)
        assertTrue(api(Intent().putExtra("position", 0)).isApiAccess)
    }

    @Test
    fun readsDocumentedParcelableArrays() {
        val api = api(
            Intent().putExtra("video_list", arrayOf(video))
                .putExtra("subs", arrayOf(subtitle))
                .putExtra("subs.name", arrayOf("English"))
                .putExtra("subs.enable", arrayOf(subtitle)),
        )
        assertEquals(listOf(video.toString()), api.getPlaylist())
        assertEquals("English", api.getSubs().single().name)
        assertTrue(api.getSubs().single().isSelected)
    }

    @Test
    fun retainsArrayListCompatibility() {
        assertEquals(
            listOf(video.toString()),
            api(Intent().putParcelableArrayListExtra("video_list", arrayListOf(video))).getPlaylist(),
        )
    }

    @Test
    fun malformedUriEntriesDoNotCrashThePlayer() {
        val intent = Intent().putExtra("subs", arrayOf(null, Intent(), subtitle))
        assertEquals(listOf(subtitle), api(intent).getSubs().map { it.uri })
    }

    @Test
    fun stoppedResultContainsLastMediaUriAndKnownTimes() {
        val result = api(Intent(Intent.ACTION_VIEW, video)).getResult(false, 2000, 1000)
        assertEquals("com.mxtech.intent.result.VIEW", result.action)
        assertEquals(video, result.data)
        assertEquals("user", result.getStringExtra("end_by"))
        assertEquals(2000, result.getIntExtra("duration", -1))
        assertEquals(1000, result.getIntExtra("position", -1))
    }

    @Test
    fun completedResultOmitsTimesAndUnknownTimesAreOmitted() {
        val completed = api(Intent()).getResult(true, 2000, 2000)
        assertEquals("playback_completion", completed.getStringExtra("end_by"))
        assertFalse(completed.hasExtra("position"))
        assertFalse(completed.hasExtra("duration"))
        val unknown = api(Intent()).getResult(false, C.TIME_UNSET, C.TIME_UNSET)
        assertFalse(unknown.hasExtra("position"))
        assertFalse(unknown.hasExtra("duration"))
    }

    @Test
    fun invalidPositionDoesNotOverrideResumeAndLongResultsDoNotOverflow() {
        assertNull(api(Intent().putExtra("position", -10)).position)
        assertEquals(
            Int.MAX_VALUE,
            api(Intent()).getResult(false, Long.MAX_VALUE, Long.MAX_VALUE).getIntExtra("position", -1),
        )
    }
}
