package dev.anilbeesetti.nextplayer.feature.network.navigation

import android.net.Uri
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import dev.anilbeesetti.nextplayer.feature.network.screens.addconnection.AddConnectionScreenRoute
import dev.anilbeesetti.nextplayer.feature.network.screens.addconnection.AddConnectionViewModel
import dev.anilbeesetti.nextplayer.feature.network.screens.browse.NetworkBrowseScreenRoute
import dev.anilbeesetti.nextplayer.feature.network.screens.browse.NetworkBrowseViewModel
import dev.anilbeesetti.nextplayer.feature.network.screens.list.NetworkScreenRoute
import kotlinx.serialization.Serializable

@Serializable
object NetworkRoute : NavKey

@Serializable
data class AddConnectionRoute(val connectionId: Long? = null) : NavKey

@Serializable
data class NetworkBrowseRoute(val connectionId: Long, val path: String? = null) : NavKey

fun NavBackStack<NavKey>.navigateToAddConnection(connectionId: Long? = null) {
    add(AddConnectionRoute(connectionId))
}

fun NavBackStack<NavKey>.navigateToNetworkBrowse(connectionId: Long, path: String? = null) {
    add(NetworkBrowseRoute(connectionId, path))
}

fun EntryProviderScope<NavKey>.networkEntry(
    onAddConnection: () -> Unit,
    onEditConnection: (connectionId: Long) -> Unit,
    onOpenConnection: (connectionId: Long) -> Unit,
    onSettingsClick: () -> Unit,
) {
    entry<NetworkRoute> {
        NetworkScreenRoute(
            onAddConnection = onAddConnection,
            onEditConnection = onEditConnection,
            onOpenConnection = onOpenConnection,
            onSettingsClick = onSettingsClick,
        )
    }
}

fun EntryProviderScope<NavKey>.addConnectionEntry(
    onNavigateUp: () -> Unit,
) {
    entry<AddConnectionRoute>(
        metadata = metadata {
            put(NavDisplay.TransitionKey) {
                slideInVertically { it } togetherWith scaleOut(targetScale = 0.95f)
            }
            put(NavDisplay.PopTransitionKey) {
                scaleIn(initialScale = 0.95f) togetherWith slideOutVertically { it }
            }
            put(NavDisplay.PredictivePopTransitionKey) {
                scaleIn(initialScale = 0.95f) togetherWith slideOutVertically { it }
            }
        }
    ) { key ->
        AddConnectionScreenRoute(
            onNavigateUp = onNavigateUp,
            viewModel = hiltViewModel<AddConnectionViewModel, AddConnectionViewModel.Factory>(
                creationCallback = { factory -> factory.create(key.connectionId) },
            ),
        )
    }
}

fun EntryProviderScope<NavKey>.networkBrowseEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onNavigateToFolder: (connectionId: Long, path: String) -> Unit,
) {
    entry<NetworkBrowseRoute> { key ->
        NetworkBrowseScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayVideo = onPlayVideo,
            onNavigateToFolder = onNavigateToFolder,
            viewModel = hiltViewModel<NetworkBrowseViewModel, NetworkBrowseViewModel.Factory>(
                creationCallback = { factory -> factory.create(key.connectionId, key.path) },
            ),
        )
    }
}
