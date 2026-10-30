package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackgroundPlaybackGrantsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun serviceReceivesOnlyReadAccessForDistinctQueueAndSubtitleContentUris() {
        val first = Uri.parse("content://caller/first")
        val second = Uri.parse("content://caller/second")
        val subtitle = Uri.parse("content://caller/captions")
        val items = listOf(
            MediaItem.Builder().setUri(first).setSubtitleConfigurations(
                listOf(MediaItem.SubtitleConfiguration.Builder(subtitle).build()),
            ).build(),
            MediaItem.fromUri(second),
            MediaItem.fromUri(first),
            MediaItem.fromUri("https://host/video"),
            MediaItem.fromUri("file:///video"),
        )
        context.retainPlaybackUriPermissions(
            Intent().addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            ),
            items,
        )
        val request = shadowOf(context).nextStartedService
        assertEquals(
            PlayerService::class.java.name,
            request.component!!.className,
        )
        assertEquals(
            ACTION_RETAIN_PLAYBACK_GRANTS,
            request.action,
        )
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            request.flags,
        )
        assertEquals(
            first,
            request.data,
        )
        assertEquals(
            listOf(
                first,
                subtitle,
                second,
            ),
            (0 until request.clipData!!.itemCount).map { request.clipData!!.getItemAt(it).uri },
        )
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun requestWithoutReadGrantDoesNotStartService() {
        context.retainPlaybackUriPermissions(
            Intent().addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
            listOf(MediaItem.fromUri("content://caller/video")),
        )
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun queueWithoutContentUrisDoesNotStartService() {
        context.retainPlaybackUriPermissions(
            Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            listOf(MediaItem.fromUri("https://host/video")),
        )
        assertNull(shadowOf(context).nextStartedService)
    }
}
