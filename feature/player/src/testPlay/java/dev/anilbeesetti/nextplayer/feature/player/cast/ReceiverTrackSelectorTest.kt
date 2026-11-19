package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.cast.CastTrackSelector
import androidx.media3.cast.CastTrackSelector.CastTrackSelectorRequest
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReceiverTrackSelectorTest {
    private val english = track(
        "english",
        MimeTypes.AUDIO_AAC,
    )
    private val french = track(
        "french",
        MimeTypes.AUDIO_AAC,
    )
    private val subtitles = track(
        "subtitles",
        MimeTypes.TEXT_VTT,
    )
    private val selector = ReceiverTrackSelector()

    @Test
    fun switchingAudioPreservesSubtitles() {
        val parameters = TrackSelectionParameters.DEFAULT.buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(
                    french,
                    0,
                ),
            ).build()
        assertEquals(
            setOf(
                french,
                subtitles,
            ),
            selector.evaluate(request(parameters)).selections,
        )
    }

    @Test
    fun disablingSubtitlesPreservesAudio() {
        val parameters = TrackSelectionParameters.DEFAULT.buildUpon()
            .setTrackTypeDisabled(
                C.TRACK_TYPE_TEXT,
                true,
            ).build()
        assertEquals(
            setOf(english),
            selector.evaluate(request(parameters)).selections,
        )
    }

    @Test
    fun staleOverridesDoNotRemoveReceiverAudio() {
        val parameters = TrackSelectionParameters.DEFAULT.buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(
                    track(
                        "old",
                        MimeTypes.AUDIO_AAC,
                    ),
                    0,
                ),
            ).build()
        assertEquals(
            setOf(
                english,
                subtitles,
            ),
            selector.evaluate(request(parameters)).selections,
        )
    }

    @Test
    fun receiverChangesWinAndClearStaleChoices() {
        val parameters = TrackSelectionParameters.DEFAULT.buildUpon()
            .setTrackTypeDisabled(
                C.TRACK_TYPE_TEXT,
                true,
            )
            .setOverrideForType(
                TrackSelectionOverride(
                    french,
                    0,
                ),
            ).build()
        val result = selector.evaluate(
            request(
                parameters,
                CastTrackSelector.TRACK_SELECTION_REQUEST_REASON_RECEIVER_UPDATE,
            ),
        )
        assertEquals(
            setOf(
                english,
                subtitles,
            ),
            result.selections,
        )
        assertEquals(
            emptyMap<Any, Any>(),
            result.trackSelectionParameters.overrides,
        )
        assertFalse(C.TRACK_TYPE_TEXT in result.trackSelectionParameters.disabledTrackTypes)
    }

    private fun request(
        parameters: TrackSelectionParameters,
        reason: Int = CastTrackSelector.TRACK_SELECTION_REQUEST_REASON_PARAMETER_CHANGE,
    ) = CastTrackSelectorRequest(
        1,
        parameters,
        ImmutableList.of(
            english,
            french,
            subtitles,
        ),
        ImmutableList.of(),
        ImmutableSet.of(
            english,
            subtitles,
        ),
        reason,
    )

    private fun track(id: String, mime: String) = TrackGroup(
        id,
        Format.Builder().setSampleMimeType(mime).build(),
    )
}
