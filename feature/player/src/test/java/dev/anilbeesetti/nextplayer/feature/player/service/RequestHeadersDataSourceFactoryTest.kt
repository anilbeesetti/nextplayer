package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@UnstableApi
class RequestHeadersDataSourceFactoryTest {
    @Test
    fun eachVideoSegmentAndSubtitleGetsHeadersWithoutLosingRangeOrRequestHeaders() {
        val opened = mutableListOf<DataSpec>()
        val factory = RequestHeadersDataSourceFactory(
            recordingFactory(opened),
            mapOf("Authorization" to "Bearer test"),
        )
        listOf(
            "https://host/master.m3u8",
            "https://host/segment.ts",
            "https://host/subs.vtt",
        ).forEach { uri ->
            val source = factory.createDataSource()
            source.open(
                DataSpec.Builder().setUri(
                    uri,
                ).setPosition(
                    40,
                ).setLength(80).setHttpRequestHeaders(mapOf("Range" to "custom")).build(),
            )
            source.close()
        }
        assertEquals(
            3,
            opened.size,
        )
        opened.forEach { spec ->
            assertEquals(
                mapOf(
                    "Authorization" to "Bearer test",
                    "Range" to "custom",
                ),
                spec.httpRequestHeaders,
            )
            assertEquals(
                40L,
                spec.position,
            )
            assertEquals(
                80L,
                spec.length,
            )
        }
    }

    @Test
    fun localSourcesAndLaterUnrelatedRequestsNeverReceiveApiHeaders() {
        val opened = mutableListOf<DataSpec>()
        val delegate = recordingFactory(opened)
        RequestHeadersDataSourceFactory(
            delegate,
            mapOf("Authorization" to "Bearer test"),
        )
            .createDataSource().open(DataSpec(Uri.parse("content://host/video")))
        RequestHeadersDataSourceFactory(
            delegate,
            emptyMap(),
        )
            .createDataSource().open(DataSpec(Uri.parse("https://host/other.mp4")))
        assertTrue(opened.all { it.httpRequestHeaders.isEmpty() })
    }

    private fun recordingFactory(opened: MutableList<DataSpec>) = DataSource.Factory {
        object : DataSource {
            override fun open(dataSpec: DataSpec): Long {
                opened.add(dataSpec)
                return dataSpec.length
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
            override fun getUri(): Uri? = null
            override fun close() = Unit
            override fun addTransferListener(transferListener: TransferListener) = Unit
        }
    }
}
