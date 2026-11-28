package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoShareIntentTest {
    @Test
    fun chooserGrantsReadAccessToCurrentVideo() {
        val uri = Uri.parse("content://media/external/video/media/42")

        val chooser = createVideoShareIntent(uri)
        val send = requireNotNull(
            chooser.getParcelableExtra(
                Intent.EXTRA_INTENT,
                Intent::class.java,
            ),
        )

        assertEquals(
            Intent.ACTION_CHOOSER,
            chooser.action,
        )
        assertEquals(
            Intent.ACTION_SEND,
            send.action,
        )
        assertEquals(
            "video/*",
            send.type,
        )
        assertEquals(
            uri,
            send.getParcelableExtra(
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ),
        )
        assertEquals(
            uri,
            send.clipData?.getItemAt(0)?.uri,
        )
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            send.flags,
        )
    }
}
