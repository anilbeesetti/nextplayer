package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.feature.network.navigation.NetworkRoute
import dev.anilbeesetti.nextplayer.feature.network.navigation.addConnectionScreen
import dev.anilbeesetti.nextplayer.feature.network.navigation.navigateToAddConnection
import dev.anilbeesetti.nextplayer.feature.network.navigation.navigateToNetworkBrowse
import dev.anilbeesetti.nextplayer.feature.network.navigation.networkBrowseScreen
import dev.anilbeesetti.nextplayer.feature.network.navigation.networkScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings
import kotlinx.serialization.Serializable

@Serializable
data object NetworkRootRoute

fun NavGraphBuilder.networkNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<NetworkRootRoute>(startDestination = NetworkRoute) {
        networkScreen(
            onAddConnection = { navController.navigateToAddConnection() },
            onEditConnection = { id -> navController.navigateToAddConnection(id) },
            onOpenConnection = { id -> navController.navigateToNetworkBrowse(id) },
            onSettingsClick = navController::navigateToSettings,
        )

        addConnectionScreen(
            onNavigateUp = navController::navigateUp,
        )

        networkBrowseScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri -> context.startPlayback(listOf(uri)) },
            onNavigateToFolder = { id, path -> navController.navigateToNetworkBrowse(id, path) },
        )
    }
}
