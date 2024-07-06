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
        )
        mediaPickerFolderScreen(
            onNavigateUp = navController::navigateUp,
            onVideoClick = context::startPlayerActivity,
        )
    }
}

fun Context.startPlayerActivity(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri, this, PlayerActivity::class.java)
    startActivity(intent)
}
