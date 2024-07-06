package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.MediaPickerRoute

const val mediaPickerNavigationRoute = "media_picker_screen"

fun NavController.navigateToMediaPickerScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(mediaPickerNavigationRoute, navOptions)
}

fun NavGraphBuilder.mediaPickerScreen(
    onSettingsClick: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (path: String) -> Unit,
) {
    animatedComposable(route = mediaPickerNavigationRoute) {
        MediaPickerRoute(
            onSettingsClick = onSettingsClick,
            onPlayVideo = onPlayVideo,
            onFolderClick = onFolderClick,
        )
    }
}
