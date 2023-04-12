package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.foldervideo.FolderVideoPickerScreen

const val folderVideoPickerScreenRoute = "folder_video_picker_screen"
internal const val folderIdArg = "folderId"

internal class FolderArgs(val folderId: String) {
    constructor(savedStateHandle: SavedStateHandle):
            this(Uri.decode(checkNotNull(savedStateHandle[folderIdArg])))
}

fun NavController.navigateToFolderVideoPickerScreen(folderId: String, navOptions: NavOptions? = null) {
    val encodedFolderId = Uri.encode(folderId)
    this.navigate("$folderVideoPickerScreenRoute/$encodedFolderId", navOptions)
}

fun NavGraphBuilder.folderVideoPickerScreen(
    onNavigateUp: () -> Unit,
    onVideoItemClick: (uri: Uri) -> Unit,
) {
    composable(
        route = "$folderVideoPickerScreenRoute/{$folderIdArg}",
        arguments = listOf(
            navArgument(folderIdArg) { type = NavType.StringType }
        )
    ) {
        FolderVideoPickerScreen(
            onVideoItemClick = onVideoItemClick,
            onNavigateUp = onNavigateUp
        )
    }
}
