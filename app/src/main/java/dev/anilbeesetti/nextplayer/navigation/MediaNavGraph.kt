package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerFolderScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerNavigationRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToMediaPickerFolderScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToWebDav
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToWebDavBrowser
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.webDavBrowserScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.webDavScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings

const val MEDIA_ROUTE = "media_nav_route"

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation(
        startDestination = mediaPickerNavigationRoute,
        route = MEDIA_ROUTE,
    ) {
        mediaPickerScreen(
            onPlayVideo = context::startPlayerActivity,
            onFolderClick = navController::navigateToMediaPickerFolderScreen,
            onSettingsClick = navController::navigateToSettings,
            onWebDavClick = navController::navigateToWebDav,
        )
        mediaPickerFolderScreen(
            onNavigateUp = navController::navigateUp,
            onVideoClick = context::startPlayerActivity,
            onFolderClick = navController::navigateToMediaPickerFolderScreen,
        )
        webDavScreen(
            onNavigateUp = navController::navigateUp,
            onServerClick = { server ->
                navController.navigateToWebDavBrowser(server.id)
            },
        )
        webDavBrowserScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri, username, password ->
                context.startPlayerActivityWithWebDav(uri, username, password)
            },
        )
    }
}

fun Context.startPlayerActivity(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri, this, PlayerActivity::class.java)
    startActivity(intent)
}

fun Context.startPlayerActivityWithWebDav(uri: Uri, username: String?, password: String?) {
    val intent = Intent(Intent.ACTION_VIEW, uri, this, PlayerActivity::class.java).apply {
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            putExtra("webdav_username", username)
            putExtra("webdav_password", password)
        }
    }
    startActivity(intent)
}
