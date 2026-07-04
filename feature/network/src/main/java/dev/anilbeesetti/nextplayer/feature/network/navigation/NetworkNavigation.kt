package dev.anilbeesetti.nextplayer.feature.network.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.feature.network.screens.addconnection.AddConnectionScreenRoute
import dev.anilbeesetti.nextplayer.feature.network.screens.browse.NetworkBrowseScreenRoute
import dev.anilbeesetti.nextplayer.feature.network.screens.list.NetworkScreenRoute
import kotlinx.serialization.Serializable

@Serializable
data object NetworkRoute

@Serializable
data class AddConnectionRoute(val connectionId: Long? = null)

@Serializable
data class NetworkBrowseRoute(val connectionId: Long, val path: String? = null)

fun NavController.navigateToNetwork(navOptions: NavOptions? = null) = navigate(NetworkRoute, navOptions)

fun NavController.navigateToAddConnection(connectionId: Long? = null) =
    navigate(AddConnectionRoute(connectionId))

fun NavController.navigateToNetworkBrowse(connectionId: Long, path: String? = null) =
    navigate(NetworkBrowseRoute(connectionId, path?.let { Uri.encode(it) }))

fun NavGraphBuilder.networkScreen(
    onAddConnection: () -> Unit,
    onEditConnection: (connectionId: Long) -> Unit,
    onOpenConnection: (connectionId: Long) -> Unit,
    onSettingsClick: () -> Unit,
) {
    composable<NetworkRoute> {
        NetworkScreenRoute(
            onAddConnection = onAddConnection,
            onEditConnection = onEditConnection,
            onOpenConnection = onOpenConnection,
            onSettingsClick = onSettingsClick,
        )
    }
}

fun NavGraphBuilder.addConnectionScreen(
    onNavigateUp: () -> Unit,
) {
    composable<AddConnectionRoute> {
        AddConnectionScreenRoute(onNavigateUp = onNavigateUp)
    }
}

fun NavGraphBuilder.networkBrowseScreen(
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onNavigateToFolder: (connectionId: Long, path: String) -> Unit,
) {
    composable<NetworkBrowseRoute> {
        NetworkBrowseScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayVideo = onPlayVideo,
            onNavigateToFolder = onNavigateToFolder,
        )
    }
}
