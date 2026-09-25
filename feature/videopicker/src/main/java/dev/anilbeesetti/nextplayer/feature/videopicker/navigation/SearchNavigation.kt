package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.search.SearchScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.search.SearchViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
        val viewModel = koinViewModel<SearchViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        SearchScreen(viewModel = viewModel)
    }
}
