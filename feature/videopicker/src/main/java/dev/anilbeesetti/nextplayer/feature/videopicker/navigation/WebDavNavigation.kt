package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.model.WebDavServer
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.webdav.WebDavRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.webdav.WebDavBrowserRoute

const val webDavNavigationRoute = "webdav_screen"
const val webDavBrowserNavigationRoute = "webdav_browser_screen/{serverId}"

fun NavController.navigateToWebDav(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(webDavNavigationRoute, navOptions)
}

fun NavController.navigateToWebDavBrowser(serverId: String, navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate("webdav_browser_screen/$serverId", navOptions)
}

fun NavGraphBuilder.webDavScreen(
    onNavigateUp: () -> Unit,
    onServerClick: (WebDavServer) -> Unit,
) {
    animatedComposable(route = webDavNavigationRoute) {
        WebDavRoute(
            onNavigateUp = onNavigateUp,
            onServerClick = onServerClick,
        )
    }
}

fun NavGraphBuilder.webDavBrowserScreen(
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri, String?, String?) -> Unit,
) {
    animatedComposable(route = webDavBrowserNavigationRoute) { backStackEntry ->
        val serverId = backStackEntry.arguments?.getString("serverId") ?: return@animatedComposable
        WebDavBrowserRoute(
            serverId = serverId,
            onNavigateUp = onNavigateUp,
            onPlayVideo = { uri, username, password ->
                onPlayVideo(uri, username, password)
            },
        )
    }
}
