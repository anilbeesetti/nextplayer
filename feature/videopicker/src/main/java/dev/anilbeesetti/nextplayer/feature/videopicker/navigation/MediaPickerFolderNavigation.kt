package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder.MediaPickerFolderRoute

const val MEDIA_PICKER_FOLDER_NAVIGATION_ROUTE = "media_picker_folder_screen"
internal const val FOLDER_ID_ARG = "folderId"

internal class FolderArgs(val folderId: String) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(Uri.decode(checkNotNull(savedStateHandle[FOLDER_ID_ARG])))
}

fun NavController.navigateToMediaPickerFolderScreen(
    folderId: String,
    navOptions: NavOptions? = navOptions { launchSingleTop = true }
) {
    val encodedFolderId = Uri.encode(folderId)
    this.navigate("$MEDIA_PICKER_FOLDER_NAVIGATION_ROUTE/$encodedFolderId", navOptions)
}

fun NavGraphBuilder.mediaPickerFolderScreen(
    onNavigateUp: () -> Unit,
    onVideoClick: (uri: Uri) -> Unit
) {
    animatedComposable(
        route = "$MEDIA_PICKER_FOLDER_NAVIGATION_ROUTE/{$FOLDER_ID_ARG}",
        arguments = listOf(
            navArgument(FOLDER_ID_ARG) { type = NavType.StringType }
        )
    ) {
        MediaPickerFolderRoute(
            onVideoClick = onVideoClick,
            onNavigateUp = onNavigateUp
        )
    }
}
