package dev.anilbeesetti.nextplayer.feature.player.cast

import android.net.Uri
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CastResourceCleanupTest {
    @Test
    fun asynchronousRemoteTimelineKeepsVideoReachableUntilReturningToPhone() {
        val context = RuntimeEnvironment.getApplication()
        val local = ExoPlayer.Builder(context).build()
        val remoteExo = ExoPlayer.Builder(context).build()
        val remote = object : ForwardingPlayer(remoteExo) {
            override fun getDeviceInfo(): DeviceInfo = DeviceInfo.Builder(
                DeviceInfo.PLAYBACK_TYPE_REMOTE,
            ).build()
        }
        val output = object : ForwardingSimpleBasePlayer(local) {
            fun switchTo(player: Player) = setPlayer(player)
        }
        val server = LocalCastServer(
            context,
            DataSource.Factory {
                ByteArrayDataSource(
                    byteArrayOf(
                        1,
                        2,
                        3,
                    ),
                )
            },
        )
        try {
            local.setMediaItem(MediaItem.fromUri("file:///video.mp4"))
            output.addListener(CastResourceCleanup(output) { server.stop() })
            val url = server.register(
                Uri.parse("file:///video.mp4"),
                "video/mp4",
                "127.0.0.1",
            )
            // CastPlayer switches before the receiver has acknowledged the submitted queue.
            output.switchTo(remote)
            assertEquals(
                DeviceInfo.PLAYBACK_TYPE_REMOTE,
                output.deviceInfo.playbackType,
            )
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                assertEquals(
                    200,
                    connection.responseCode,
                )
                assertEquals(
                    listOf<Byte>(
                        1,
                        2,
                        3,
                    ),
                    connection.inputStream.readBytes().toList(),
                )
            } finally {
                connection.disconnect()
            }
            output.switchTo(local)
            assertFalse(server.isAlive)
        } finally {
            server.stop()
            output.release()
            remoteExo.release()
        }
    }
}
