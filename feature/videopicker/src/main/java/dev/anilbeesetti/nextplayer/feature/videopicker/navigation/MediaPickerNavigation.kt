package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.MediaPickerRoute

const val MEDIA_PICKER_NAVIGATION_ROUTE = "media_picker_screen"

fun NavController.navigateToMediaPickerScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(MEDIA_PICKER_NAVIGATION_ROUTE, navOptions)
}

fun NavGraphBuilder.mediaPickerScreen(
    onSettingsClick: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (path: String) -> Unit
) {
    animatedComposable(route = MEDIA_PICKER_NAVIGATION_ROUTE) {
        MediaPickerRoute(
            onSettingsClick = onSettingsClick,
            onPlayVideo = onPlayVideo,
            onFolderClick = onFolderClick
        )
    }
}
