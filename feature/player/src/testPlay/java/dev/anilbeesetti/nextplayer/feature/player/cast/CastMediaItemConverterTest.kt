package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import dev.anilbeesetti.nextplayer.feature.player.extensions.withExternalAudio
import java.net.InetAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CastMediaItemConverterTest {
    private val context = RuntimeEnvironment.getApplication()
    private val server = LocalCastServer(
        RuntimeEnvironment.getApplication(),
        DataSource.Factory {
            ByteArrayDataSource(byteArrayOf(1))
        },
    )
    private val converter = CastMediaItemConverter(
        context,
        server,
    )

    @After
    fun tearDown() = server.stop()

    @Test
    fun localVideoUsesLanUrlAndRoundTripsOriginalMetadataAndUri() {
        val connectivity = context.getSystemService(
            Context.CONNECTIVITY_SERVICE,
        ) as ConnectivityManager
        shadowOf(connectivity).setNetworkCapabilities(
            connectivity.activeNetwork,
            ReflectionHelpers.callConstructor(NetworkCapabilities::class.java).apply {
                ReflectionHelpers.callInstanceMethod<Any>(
                    this,
                    "addTransportType",
                    ClassParameter.from(
                        Int::class.javaPrimitiveType,
                        NetworkCapabilities.TRANSPORT_WIFI,
                    ),
                )
            },
        )
        shadowOf(connectivity).setLinkProperties(
            connectivity.activeNetwork,
            LinkProperties().apply {
                val address = ReflectionHelpers.callConstructor(
                    LinkAddress::class.java,
                    ClassParameter.from(
                        InetAddress::class.java,
                        InetAddress.getByName("192.168.1.12"),
                    ),
                    ClassParameter.from(
                        Int::class.javaPrimitiveType,
                        24,
                    ),
                )
                ReflectionHelpers.callInstanceMethod<Any>(
                    this,
                    "addLinkAddress",
                    ClassParameter.from(
                        LinkAddress::class.java,
                        address,
                    ),
                )
            },
        )
        val item = item(
            "content://media/external/video/media/42",
        ).buildUpon().setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse("file:///movie.srt"))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP).build(),
            ),
        ).build()
        val queueItem = converter.toMediaQueueItem(item)
        val info = checkNotNull(queueItem.media)
        assertTrue(checkNotNull(info.contentUrl).startsWith("http://192.168.1.12:"))
        assertEquals(
            "video/mp4",
            info.contentType,
        )
        val subtitle = checkNotNull(info.mediaTracks).single()
        assertTrue(checkNotNull(subtitle.contentId).startsWith("http://192.168.1.12:"))
        assertEquals(
            MimeTypes.TEXT_VTT,
            subtitle.contentType,
        )
        assertEquals(
            120_000L,
            info.streamDuration,
        )
        assertSame(
            item,
            converter.toMediaItem(queueItem),
        )
        converter.clear()
        assertEquals(
            item.localConfiguration?.uri,
            converter.toMediaItem(queueItem).localConfiguration?.uri,
        )
    }

    @Test
    fun publicStreamsArePassedDirectlyWithoutStartingServer() {
        val item = item("https://example.com/video.mp4")
        val queueItem = converter.toMediaQueueItem(item)
        assertEquals(
            item.localConfiguration?.uri.toString(),
            queueItem.media?.contentUrl,
        )
        assertEquals(
            -1,
            server.listeningPort,
        )
        assertSame(
            item,
            converter.toMediaItem(queueItem),
        )
    }

    @Test
    fun missingAddressDoesNotCrashPlayback() {
        val connectivity = context.getSystemService(
            Context.CONNECTIVITY_SERVICE,
        ) as ConnectivityManager
        shadowOf(connectivity).clearAllNetworks()
        val item = item("file:///storage/video.mp4")
        assertEquals(
            item.mediaId,
            converter.toMediaQueueItem(item).media?.contentId,
        )
    }

    @Test
    fun contentUriTypeComesFromItsFileNameTitleWithoutQueryingTheProvider() {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping(
            "mkv",
            "video/x-matroska",
        )
        shadowOf(
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
        ).clearAllNetworks()
        val uri = "content://media/external/video/media/7"
        val item = MediaItem.Builder().setMediaId(uri).setUri(Uri.parse(uri))
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Movie (2026).mkv").build())
            .build()
        assertEquals(
            "video/x-matroska",
            converter.toMediaQueueItem(item).media?.contentType,
        )
    }

    @Test
    fun externalSubtitlesHaveReceiverTracksAndDefaultSelection() {
        val subtitle = MediaItem.SubtitleConfiguration.Builder(
            Uri.parse("https://example.com/captions.vtt"),
        )
            .setMimeType(MimeTypes.TEXT_VTT).setLabel("English").setLanguage("en")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val item = item(
            "https://example.com/video.mp4",
        ).buildUpon().setSubtitleConfigurations(listOf(subtitle)).build()
        val queueItem = converter.toMediaQueueItem(item)
        val track = checkNotNull(queueItem.media?.mediaTracks).single()
        assertEquals(
            subtitle.uri.toString(),
            track.contentId,
        )
        assertEquals(
            MimeTypes.TEXT_VTT,
            track.contentType,
        )
        assertEquals(
            "English",
            track.name,
        )
        assertEquals(
            "en",
            track.language,
        )
        assertTrue(checkNotNull(queueItem.activeTrackIds).contentEquals(longArrayOf(track.id)))
        assertSame(
            item,
            converter.toMediaItem(queueItem),
        )
        assertEquals(
            -1,
            server.listeningPort,
        )
    }

    @Test
    fun differentUrisWithDefaultMediaIdsKeepTheirOwnItems() {
        val first = item("https://example.com/first.mp4").buildUpon().setMediaId("").build()
        val second = item("https://example.com/second.mp4").buildUpon().setMediaId("").build()
        val firstQueueItem = converter.toMediaQueueItem(first)
        val secondQueueItem = converter.toMediaQueueItem(second)
        assertNotEquals(
            first.localConfiguration?.uri,
            second.localConfiguration?.uri,
        )
        assertSame(
            first,
            converter.toMediaItem(firstQueueItem),
        )
        assertSame(
            second,
            converter.toMediaItem(secondQueueItem),
        )
    }

    private fun item(uri: String): MediaItem = MediaItem.Builder()
        .setMediaId(uri)
        .setUri(Uri.parse(uri))
        .setMimeType("video/mp4")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Local movie")
                .setDurationMs(120_000)
                .setExtras(
                    Bundle().apply {
                        putLong(
                            "position",
                            40_000,
                        )
                    },
                )
                .build(),
        )
        .build().withExternalAudio(listOf(Uri.parse("file:///audio.aac")))
}
