package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.MediaPickerRoute

const val mediaPickerNavigationRoute = "media_picker_screen"

fun NavController.navigateToMediaPickerScreen(navOptions: NavOptions? = null) {
    this.navigate(mediaPickerNavigationRoute, navOptions)
}

fun NavGraphBuilder.mediaPickerScreen(
    onSettingsClick: () -> Unit,
    onVideoClick: (uri: Uri) -> Unit,
    onFolderCLick: (path: String) -> Unit
) {
    composable(route = mediaPickerNavigationRoute) {
        MediaPickerRoute(
            onSettingsClick = onSettingsClick,
            onVideoClick = onVideoClick,
            onFolderClick = onFolderCLick
        )
    }
}
