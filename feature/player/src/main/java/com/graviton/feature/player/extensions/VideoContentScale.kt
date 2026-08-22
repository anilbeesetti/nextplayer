package com.graviton.feature.player.extensions

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FixedScale
import com.graviton.core.model.VideoContentScale
import com.graviton.core.ui.R

fun VideoContentScale.nameRes(): Int = when (this) {
    VideoContentScale.BEST_FIT -> R.string.best_fit
    VideoContentScale.STRETCH -> R.string.stretch
    VideoContentScale.CROP -> R.string.crop
    VideoContentScale.HUNDRED_PERCENT -> R.string.hundred_percent
    VideoContentScale.SIXTEEN_NINE -> R.string.sixteen_nine
    VideoContentScale.FOUR_THREE -> R.string.four_three
}

fun VideoContentScale.drawableRes(): Int = when (this) {
    VideoContentScale.BEST_FIT -> R.drawable.ic_fit_screen
    VideoContentScale.STRETCH -> R.drawable.ic_aspect_ratio
    VideoContentScale.CROP -> R.drawable.ic_crop_landscape
    VideoContentScale.HUNDRED_PERCENT -> R.drawable.ic_width_wide
    VideoContentScale.SIXTEEN_NINE -> R.drawable.ic_aspect_ratio
    VideoContentScale.FOUR_THREE -> R.drawable.ic_aspect_ratio
}

fun VideoContentScale.toContentScale(): ContentScale = when (this) {
    VideoContentScale.BEST_FIT -> ContentScale.Fit
    VideoContentScale.STRETCH -> ContentScale.FillBounds
    VideoContentScale.CROP -> ContentScale.Crop
    VideoContentScale.HUNDRED_PERCENT -> FixedScale(1.0f) // TODO: fix this
    VideoContentScale.SIXTEEN_NINE -> ContentScale.FillBounds // Pseudo
    VideoContentScale.FOUR_THREE -> ContentScale.FillBounds // Pseudo
}
