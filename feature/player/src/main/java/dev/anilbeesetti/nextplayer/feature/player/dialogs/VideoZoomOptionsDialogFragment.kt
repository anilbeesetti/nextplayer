package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import dev.anilbeesetti.nextplayer.core.ui.R

@UnstableApi
fun Activity.videoZoomOptionsDialog(
    currentVideoZoom: VideoZoom,
    onVideoZoomOptionSelected: (videoZoom: VideoZoom) -> Unit,
): Dialog {
    val videoZoomValues = VideoZoom.entries.toTypedArray()

    return MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.video_zoom))
        .setSingleChoiceItems(
            videoZoomValues.map { getString(it.nameRes()) }.toTypedArray(),
            videoZoomValues.indexOfFirst { it == currentVideoZoom },
        ) { dialog, trackIndex ->
            onVideoZoomOptionSelected(videoZoomValues[trackIndex])
            dialog.dismiss()
        }.create()
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
