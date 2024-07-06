package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import dev.anilbeesetti.nextplayer.core.ui.R

@UnstableApi
class VideoZoomOptionsDialogFragment(
    private val currentVideoZoom: VideoZoom,
    private val onVideoZoomOptionSelected: (videoZoom: VideoZoom) -> Unit,
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val videoZoomValues = VideoZoom.entries.toTypedArray()

        return activity?.let { activity ->
            MaterialAlertDialogBuilder(activity)
                .setTitle(getString(R.string.video_zoom))
                .setSingleChoiceItems(
                    videoZoomValues.map { getString(it.nameRes()) }.toTypedArray(),
                    videoZoomValues.indexOfFirst { it == currentVideoZoom },
                ) { dialog, trackIndex ->
                    onVideoZoomOptionSelected(videoZoomValues[trackIndex])
                    dialog.dismiss()
                }.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}

fun VideoZoom.nameRes(): Int {
    val stringRes = when (this) {
        VideoZoom.BEST_FIT -> R.string.best_fit
        VideoZoom.STRETCH -> R.string.stretch
        VideoZoom.CROP -> R.string.crop
        VideoZoom.HUNDRED_PERCENT -> R.string.hundred_percent
    }

    return stringRes
}
