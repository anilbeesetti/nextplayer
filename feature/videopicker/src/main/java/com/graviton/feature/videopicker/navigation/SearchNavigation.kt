package com.graviton.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.feature.videopicker.screens.search.SearchRoute
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
        SearchRoute(
            onPlayVideo = onPlayVideo,
            onNavigateUp = onNavigateUp,
            onFolderClick = onFolderClick,
        )
    }
}
