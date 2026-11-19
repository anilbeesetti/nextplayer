package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.cast.CastTrackSelector
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableSet

/** Apply explicit choices, but let receiver-side changes remain authoritative. */
@UnstableApi
internal class ReceiverTrackSelector : CastTrackSelector() {
    override fun evaluate(request: CastTrackSelectorRequest): CastTrackSelectorResult {
        if (request.trackSelectionRequestReason !=
            TRACK_SELECTION_REQUEST_REASON_PARAMETER_CHANGE
        ) {
            val parameters = request.trackSelectionParameters.buildUpon()
            for (type in listOf(
                C.TRACK_TYPE_AUDIO,
                C.TRACK_TYPE_TEXT,
            )) {
                parameters.clearOverridesOfType(type)
                parameters.setTrackTypeDisabled(
                    type,
                    request.trackGroupList.any { it.type == type } &&
                        request.currentlySelectedTrackGroups.none { it.type == type },
                )
            }
            return request.buildResultUpon().setTrackSelectionParameters(parameters.build()).build()
        }
        val parameters = request.trackSelectionParameters
        val selected = request.currentlySelectedTrackGroups.toMutableSet()
        for (type in listOf(
            C.TRACK_TYPE_AUDIO,
            C.TRACK_TYPE_TEXT,
        )) {
            val override = parameters.overrides.values.firstOrNull {
                it.type == type && it.mediaTrackGroup in request.trackGroupList
            }
            when {
                type in parameters.disabledTrackTypes -> selected.removeAll { it.type == type }

                override != null -> {
                    selected.removeAll { it.type == type }
                    if (override.trackIndices.isNotEmpty()) selected.add(override.mediaTrackGroup)
                }
            }
        }
        return request.buildResultUpon().setSelections(ImmutableSet.copyOf(selected)).build()
    }
}
