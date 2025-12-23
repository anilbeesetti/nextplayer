package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FixedScale
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.R

fun VideoContentScale.nameRes(): Int = when (this) {
    VideoContentScale.BEST_FIT -> R.string.best_fit
    VideoContentScale.STRETCH -> R.string.stretch
    VideoContentScale.CROP -> R.string.crop
    VideoContentScale.HUNDRED_PERCENT -> R.string.hundred_percent
}

fun VideoContentScale.drawableRes(): Int = when (this) {
    VideoContentScale.BEST_FIT -> R.drawable.ic_fit_screen
    VideoContentScale.STRETCH -> R.drawable.ic_aspect_ratio
    VideoContentScale.CROP -> R.drawable.ic_crop_landscape
    VideoContentScale.HUNDRED_PERCENT -> R.drawable.ic_width_wide
}

fun VideoContentScale.toContentScale(): ContentScale = when (this) {
    VideoContentScale.BEST_FIT -> ContentScale.Fit
    VideoContentScale.STRETCH -> ContentScale.FillBounds
    VideoContentScale.CROP -> ContentScale.Crop
    VideoContentScale.HUNDRED_PERCENT -> FixedScale(1.0f) // TODO: fix this
}
