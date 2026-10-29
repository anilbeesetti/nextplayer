package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.positionMs
import dev.anilbeesetti.nextplayer.feature.player.extensions.uriToSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoDecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlayerApiTest {
    private val video = Uri.parse("https://example.com/video.mp4")
    private val subtitle = Uri.parse("https://example.com/sub.srt")

    private fun api(intent: Intent) = PlayerApi(intent)

    @Test
    fun playbackSpeedAcceptsPositiveFiniteNumbersAndRejectsInvalidInput() {
        assertNull(api(Intent()).playbackSpeed)
        listOf(
            0.5f,
            1f,
            1.25f,
            2f,
            8f,
        ).forEach { speed ->
            assertEquals(
                speed,
                api(
                    Intent().putExtra(
                        "playback_speed",
                        speed,
                    ),
                ).playbackSpeed,
            )
        }
        assertEquals(
            2f,
            api(
                Intent().putExtra(
                    "playback_speed",
                    2,
                ),
            ).playbackSpeed,
        )
        assertEquals(
            1.5f,
            api(
                Intent().putExtra(
                    "playback_speed",
                    1.5,
                ),
            ).playbackSpeed,
        )
        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
        ).forEach { speed ->
            assertNull(
                api(
                    Intent().putExtra(
                        "playback_speed",
                        speed,
                    ),
                ).playbackSpeed,
            )
        }
        assertNull(
            api(
                Intent().putExtra(
                    "playback_speed",
                    "fast",
                ),
            ).playbackSpeed,
        )
    }

    @Test
    fun speedAndDirectBackgroundLaunchAreRecognizedAsApiRequests() {
        assertTrue(
            api(
                Intent().putExtra(
                    "playback_speed",
                    1.5f,
                ),
            ).isApiAccess,
        )
        assertTrue(
            api(
                Intent().putExtra(
                    "background",
                    true,
                ),
            ).isApiAccess,
        )
        assertTrue(
            api(
                Intent().putExtra(
                    "background",
                    true,
                ),
            ).launchInBackground,
        )
        assertFalse(api(Intent()).launchInBackground)
        assertFalse(
            api(
                Intent().putExtra(
                    "background",
                    false,
                ),
            ).launchInBackground,
        )
        assertFalse(
            api(
                Intent().putExtra(
                    "background",
                    "true",
                ),
            ).launchInBackground,
        )
    }

    @Test
    fun directBackgroundLaunchIsIndependentOfStickyAndDoesNotLeakToAnotherRequest() {
        val request = api(
            Intent().putExtra(
                "background",
                true,
            ).putExtra(
                "sticky",
                false,
            ),
        )
        assertTrue(request.launchInBackground)
        assertEquals(
            false,
            request.sticky,
        )
        assertFalse(
            api(
                Intent().putExtra(
                    "sticky",
                    true,
                ),
            ).launchInBackground,
        )
        assertFalse(api(Intent()).launchInBackground)
        assertNull(api(Intent()).playbackSpeed)
    }

    @Test
    fun intentMimeTypeOnlyAppliesToTheRequestedVideoInAMixedPlaylist() {
        val api = api(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                video,
                "video/mp4",
            ),
        )
        val items = api.createMediaItems(
            listOf(
                video.toString(),
                "https://host/stream.m3u8",
            ),
            0,
            emptyList(),
        )
        assertEquals(
            "video/mp4",
            items[0].localConfiguration!!.mimeType,
        )
        assertNull(items[1].localConfiguration!!.mimeType)
    }

    @Test
    fun repeatedPlaylistUrisKeepTheirIndividualNames() {
        val api = api(
            Intent(
                Intent.ACTION_VIEW,
                video,
            ).putExtra(
                "video_list",
                arrayOf(
                    video,
                    video,
                ),
            )
                .putExtra(
                    "video_list.name",
                    arrayOf(
                        "First play",
                        "Encore",
                    ),
                ),
        )
        assertEquals(
            listOf(
                "First play",
                "Encore",
            ),
            api.createMediaItems(
                api.getPlaylist(),
                0,
                emptyList(),
            ).map { it.mediaMetadata.title },
        )
    }

    @Test
    fun falseReturnResultDoesNotRequestCallback() {
        assertFalse(
            api(
                Intent().putExtra(
                    "return_result",
                    false,
                ),
            ).shouldReturnResult,
        )
        assertTrue(
            api(
                Intent().putExtra(
                    "return_result",
                    true,
                ),
            ).shouldReturnResult,
        )
    }

    @Test
    fun unrelatedExtrasAreNotAnApiRequest() {
        assertFalse(
            api(
                Intent().putExtra(
                    "unrelated",
                    true,
                ),
            ).isApiAccess,
        )
        assertTrue(
            api(
                Intent().putExtra(
                    "position",
                    0,
                ),
            ).isApiAccess,
        )
    }

    @Test
    fun readsDocumentedParcelableArrays() {
        val api = api(
            Intent().putExtra(
                "video_list",
                arrayOf(video),
            )
                .putExtra(
                    "subs",
                    arrayOf(subtitle),
                )
                .putExtra(
                    "subs.name",
                    arrayOf("English"),
                )
                .putExtra(
                    "subs.enable",
                    arrayOf(subtitle),
                ),
        )
        assertEquals(
            listOf(video.toString()),
            api.getPlaylist(),
        )
        assertEquals(
            "English",
            api.getSubs().single().name,
        )
        assertTrue(api.getSubs().single().isSelected)
    }

    @Test
    fun retainsArrayListCompatibility() {
        assertEquals(
            listOf(video.toString()),
            api(
                Intent().putParcelableArrayListExtra(
                    "video_list",
                    arrayListOf(video),
                ),
            ).getPlaylist(),
        )
    }

    @Test
    fun malformedUriEntriesDoNotCrashThePlayer() {
        val intent = Intent().putExtra(
            "subs",
            arrayOf(
                null,
                Intent(),
                subtitle,
            ),
        )
        assertEquals(
            listOf(subtitle),
            api(intent).getSubs().map { it.uri },
        )
    }

    @Test
    fun stoppedResultContainsLastMediaUriAndKnownTimes() {
        val result = api(
            Intent(
                Intent.ACTION_VIEW,
                video,
            ),
        ).getResult(
            false,
            2000,
            1000,
        )
        assertEquals(
            "com.mxtech.intent.result.VIEW",
            result.action,
        )
        assertEquals(
            video,
            result.data,
        )
        assertEquals(
            "user",
            result.getStringExtra("end_by"),
        )
        assertEquals(
            2000,
            result.getIntExtra(
                "duration",
                -1,
            ),
        )
        assertEquals(
            1000,
            result.getIntExtra(
                "position",
                -1,
            ),
        )
    }

    @Test
    fun completedResultOmitsTimesAndUnknownTimesAreOmitted() {
        val completed = api(Intent()).getResult(
            true,
            2000,
            2000,
        )
        assertEquals(
            "playback_completion",
            completed.getStringExtra("end_by"),
        )
        assertFalse(completed.hasExtra("position"))
        assertFalse(completed.hasExtra("duration"))
        val unknown = api(Intent()).getResult(
            false,
            C.TIME_UNSET,
            C.TIME_UNSET,
        )
        assertFalse(unknown.hasExtra("position"))
        assertFalse(unknown.hasExtra("duration"))
    }

    @Test
    fun invalidPositionDoesNotOverrideResumeAndLongResultsDoNotOverflow() {
        assertNull(
            api(
                Intent().putExtra(
                    "position",
                    -10,
                ),
            ).position,
        )
        assertEquals(
            Int.MAX_VALUE,
            api(
                Intent(),
            ).getResult(
                false,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
            ).getIntExtra(
                "position",
                -1,
            ),
        )
    }

    @Test
    fun playlistNamesOverrideTitleAndOnlySelectedItemGetsPositionAndSubs() {
        val other = Uri.parse("https://example.com/other.mp4")
        val api = api(
            Intent(
                Intent.ACTION_VIEW,
                video,
            )
                .putExtra(
                    "video_list",
                    arrayOf(
                        other,
                        video,
                    ),
                )
                .putExtra(
                    "video_list.name",
                    arrayOf(
                        "First",
                        "Second",
                    ),
                )
                .putExtra(
                    "title",
                    "Ignored",
                )
                .putExtra(
                    "position",
                    123,
                )
                .putExtra(
                    "video_list_is_explicit",
                    true,
                ),
        )
        val sub = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(subtitle).build()
        val items = api.createMediaItems(
            api.getPlaylist(),
            1,
            listOf(sub),
        )
        assertEquals(
            listOf(
                "First",
                "Second",
            ),
            items.map { it.mediaMetadata.title },
        )
        assertNull(items[0].mediaMetadata.positionMs)
        assertEquals(
            123L,
            items[1].mediaMetadata.positionMs,
        )
        assertTrue(items[0].localConfiguration!!.subtitleConfigurations.isEmpty())
        assertEquals(
            subtitle,
            items[1].localConfiguration!!.subtitleConfigurations.single().uri,
        )
        assertTrue(items.all { it.isExplicitPlaylistItem })
    }

    @Test
    fun playlistWithoutExplicitFlagRespectsAutoplayAndMissingDataIsAdded() {
        val other = Uri.parse("https://example.com/other.mp4")
        val api = api(
            Intent(
                Intent.ACTION_VIEW,
                video,
            ).putExtra(
                "video_list",
                arrayOf(other),
            ),
        )
        assertEquals(
            listOf(
                video.toString(),
                other.toString(),
            ),
            api.getPlaylist(),
        )
        assertFalse(
            api.createMediaItems(
                api.getPlaylist(),
                0,
                emptyList(),
            ).any {
                it.isExplicitPlaylistItem
            },
        )
    }

    @Test
    fun nullPlaylistNameUsesDefaultRatherThanSingleTitle() {
        val api = api(
            Intent(
                Intent.ACTION_VIEW,
                video,
            ).putExtra(
                "video_list",
                arrayOf(video),
            )
                .putExtra(
                    "video_list.name",
                    arrayOfNulls<String>(1),
                ).putExtra(
                    "title",
                    "Ignored",
                ),
        )
        assertNull(
            api.createMediaItems(
                api.getPlaylist(),
                0,
                emptyList(),
            ).single().mediaMetadata.title,
        )
    }

    @Test
    fun headersReachEveryItemAndAreNotReusedForNextRequest() {
        val api = api(
            Intent(
                Intent.ACTION_VIEW,
                video,
            ).putExtra(
                "headers",
                arrayOf(
                    "User-Agent",
                    "Client/1",
                    "Authorization",
                    "Bearer test",
                    "dangling",
                ),
            ),
        )
        val item = api.createMediaItems(
            listOf(video.toString()),
            0,
            emptyList(),
        ).single()
        assertEquals(
            mapOf(
                "User-Agent" to "Client/1",
                "Authorization" to "Bearer test",
            ),
            item.apiHeaders,
        )
        assertTrue(
            api(
                Intent(),
            ).createMediaItems(
                listOf(video.toString()),
                0,
                emptyList(),
            ).single().apiHeaders.isEmpty(),
        )
    }

    @Test
    fun decoderByteAndMimeTypeArePassedToMediaItem() {
        val api = api(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                video,
                "application/x-mpegURL",
            )
                .putExtra(
                    "decode_mode",
                    2.toByte(),
                ),
        )
        val item = api.createMediaItems(
            listOf(video.toString()),
            0,
            emptyList(),
        ).single()
        assertEquals(
            DecoderMode.FFMPEG,
            item.mediaMetadata.videoDecoderMode,
        )
        assertEquals(
            "application/x-mpegURL",
            item.localConfiguration!!.mimeType,
        )
    }

    @Test
    fun playbackOptionsKeepAbsentAndFalseDistinctAndRejectInvalidValues() {
        assertNull(api(Intent()).sticky)
        assertEquals(
            false,
            api(
                Intent().putExtra(
                    "sticky",
                    false,
                ),
            ).sticky,
        )
        assertEquals(
            9,
            api(
                Intent().putExtra(
                    "orientation",
                    9,
                ),
            ).orientation,
        )
        assertNull(
            api(
                Intent().putExtra(
                    "orientation",
                    99,
                ),
            ).orientation,
        )
        assertEquals(
            VideoContentScale.CROP,
            api(
                Intent().putExtra(
                    "video_zoom",
                    3,
                ),
            ).videoContentScale,
        )
        assertNull(
            api(
                Intent().putExtra(
                    "video_zoom",
                    99,
                ),
            ).videoContentScale,
        )
        assertEquals(
            2f,
            api(
                Intent().putExtra(
                    "DAR_horz",
                    4f,
                ).putExtra(
                    "DAR_vert",
                    2f,
                ),
            ).displayAspectRatio,
        )
        assertNull(
            api(
                Intent().putExtra(
                    "DAR_horz",
                    4f,
                ).putExtra(
                    "DAR_vert",
                    0f,
                ),
            ).displayAspectRatio,
        )
        assertFalse(
            api(
                Intent().putExtra(
                    "video",
                    false,
                ),
            ).videoVisible,
        )
        assertTrue(
            api(
                Intent().putExtra(
                    "suppress_error_message",
                    true,
                ),
            ).suppressErrorMessage,
        )
    }

    @Test
    fun allDecoderValuesAndZoomValuesUseTheirDocumentedMappings() {
        listOf(
            1 to DecoderMode.HARDWARE,
            2 to DecoderMode.FFMPEG,
            4 to DecoderMode.HARDWARE,
        ).forEach { (value, expected) ->
            val api = api(
                Intent().putExtra(
                    "decode_mode",
                    value.toByte(),
                ),
            )
            assertEquals(
                expected,
                api.createMediaItems(
                    listOf(video.toString()),
                    0,
                    emptyList(),
                ).single().mediaMetadata.videoDecoderMode,
            )
        }
        val scales = listOf(
            VideoContentScale.STRETCH,
            VideoContentScale.BEST_FIT,
            VideoContentScale.HUNDRED_PERCENT,
            VideoContentScale.CROP,
        )
        scales.forEachIndexed { index, scale ->
            assertEquals(
                scale,
                api(
                    Intent().putExtra(
                        "video_zoom",
                        index,
                    ),
                ).videoContentScale,
            )
        }
    }

    @Test
    fun malformedOptionalExtrasAreIgnored() {
        val api = api(
            Intent().putExtra(
                "position",
                "bad",
            ).putExtra(
                "title",
                17,
            )
                .putExtra(
                    "return_result",
                    "true",
                ).putExtra(
                    "headers",
                    arrayOf(
                        "Bad\r\nName",
                        "x",
                        "Valid",
                        "bad\nvalue",
                    ),
                ),
        )
        assertNull(api.position)
        assertNull(api.title)
        assertFalse(api.shouldReturnResult)
        assertTrue(
            api.createMediaItems(
                listOf(video.toString()),
                0,
                emptyList(),
            ).single().apiHeaders.isEmpty(),
        )
    }

    @Test
    fun resultReportsActualDecoderAndLastUriRatherThanRequestedValues() {
        val other = Uri.parse("https://host/last.mp4")
        val result = api(
            Intent(
                Intent.ACTION_VIEW,
                video,
            ).putExtra(
                "decode_mode",
                4.toByte(),
            ),
        ).getResult(
            false,
            2000,
            1200,
            other,
            DecoderMode.FFMPEG,
        )
        assertEquals(
            other,
            result.data,
        )
        assertEquals(
            2.toByte(),
            result.getByteExtra(
                "decode_mode",
                0,
            ),
        )
    }

    @Test
    fun networkSubtitleKeepsHeaderAwareUriAndUsesSuppliedNameAndFilename() = runTest {
        val config = RuntimeEnvironment.getApplication().uriToSubtitleConfiguration(
            uri = Uri.parse("https://host/subtitle?id=1"),
            label = "English",
            filename = "captions.vtt",
            isSelected = true,
            useMediaSourceForNetwork = true,
        )
        assertEquals(
            "English",
            config.label,
        )
        assertEquals(
            "text/vtt",
            config.mimeType,
        )
        assertEquals(
            Uri.parse("https://host/subtitle?id=1"),
            config.uri,
        )
        assertEquals(
            C.SELECTION_FLAG_DEFAULT,
            config.selectionFlags,
        )
    }
}
