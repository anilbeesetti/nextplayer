package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.core.ui.R
import kotlin.math.roundToInt

@UnstableApi
class VideoQualityDialogFragment(
    private val tracks: Tracks,
    private val onAutoSelected: () -> Unit,
    private val onQualitySelected: (groupIndex: Int, trackIndexInGroup: Int) -> Unit,
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val videoGroups = tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }

        val qualityOptions = videoGroups
            .mapIndexed { groupIndex, group ->
                val trackGroup = group.mediaTrackGroup
                (0 until trackGroup.length).map { trackIndex ->
                    QualityOption(
                        groupIndex = groupIndex,
                        trackIndexInGroup = trackIndex,
                        label = buildQualityLabel(trackGroup.getFormat(trackIndex)),
                    )
                }
            }
            .flatten()
            .distinctBy { it.label }

        return activity?.let { activity ->
            MaterialAlertDialogBuilder(activity).apply {
                setTitle(getString(R.string.video_quality))

                if (qualityOptions.size <= 1) {
                    setMessage(getString(R.string.no_video_qualities_found))
                    setPositiveButton(getString(android.R.string.ok)) { dialog, _ -> dialog.dismiss() }
                    return@apply
                }

                val labels = arrayOf(getString(R.string.auto), *qualityOptions.map { it.label }.toTypedArray())
                setSingleChoiceItems(labels, 0) { dialog, which ->
                    if (which == 0) {
                        onAutoSelected()
                    } else {
                        val option = qualityOptions[which - 1]
                        onQualitySelected(option.groupIndex, option.trackIndexInGroup)
                    }
                    dialog.dismiss()
                }
                setNegativeButton(getString(android.R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            }.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun buildQualityLabel(format: Format): String {
        val height = format.height.takeIf { it > 0 }
        val width = format.width.takeIf { it > 0 }
        val bitrate = format.bitrate.takeIf { it > 0 }

        val base = when {
            height != null -> "${height}p"
            width != null -> "${width}w"
            else -> getString(R.string.video_track)
        }

        val bitrateText = bitrate?.let {
            val mbps = (it / 1_000_000.0 * 10).roundToInt() / 10.0
            " · ${mbps}Mbps"
        }.orEmpty()

        return base + bitrateText
    }

    private data class QualityOption(
        val groupIndex: Int,
        val trackIndexInGroup: Int,
        val label: String,
    )
}
