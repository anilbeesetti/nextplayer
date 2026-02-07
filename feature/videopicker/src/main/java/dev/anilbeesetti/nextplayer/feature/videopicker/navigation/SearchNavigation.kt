package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.search.SearchRoute
import kotlinx.serialization.Serializable

@Serializable
object SearchRoute

fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    this.navigate(SearchRoute, navOptions)
}

fun NavGraphBuilder.searchScreen(
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
) {
    composable<SearchRoute> {
        SearchRoute(
            onPlayVideo = onPlayVideo,
            onNavigateUp = onNavigateUp,
            onFolderClick = onFolderClick,
        )
    }
}
