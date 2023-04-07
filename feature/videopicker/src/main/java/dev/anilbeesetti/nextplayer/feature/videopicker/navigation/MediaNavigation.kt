package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.feature.videopicker.VideoPickerScreen

const val videoPickerScreenRoute = "video_picker_screen"

fun NavController.navigateToVideoPickerScreen(navOptions: NavOptions? = null) {
    this.navigate(videoPickerScreenRoute, navOptions)
}

fun NavGraphBuilder.videoPickerScreen(onVideoItemClick: (uri: Uri) -> Unit) {
    composable(route = videoPickerScreenRoute) {
        VideoPickerScreen(onVideoItemClick = onVideoItemClick)
    }
}