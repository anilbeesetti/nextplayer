package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.compose.runtime.SideEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.search.SearchScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.search.SearchViewModel
import kotlinx.serialization.Serializable

@Serializable
object SearchRoute : NavKey

fun NavBackStack<NavKey>.navigateToSearch() {
    add(SearchRoute)
}

fun EntryProviderScope<NavKey>.searchEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
) {
    entry<SearchRoute> {
        val output = SearchViewModel.Output(
            playVideo = onPlayVideo,
            navigateUp = onNavigateUp,
            openFolder = onFolderClick,
        )
        val viewModel = hiltViewModel<SearchViewModel, SearchViewModel.Factory>(
            creationCallback = { factory -> factory.create(output = output) },
        )
        SideEffect { viewModel.output = output }
        SearchScreen(viewModel = viewModel)
    }
}
