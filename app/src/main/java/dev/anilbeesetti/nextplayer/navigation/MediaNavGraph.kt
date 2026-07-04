package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.MediaPickerRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToMediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToSearch
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToVault
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.searchScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.vaultScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings
import kotlinx.serialization.Serializable

@Serializable
data object MediaRootRoute

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<MediaRootRoute>(startDestination = MediaPickerRoute()) {
        mediaPickerScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri -> context.startPlayback(listOf(uri)) },
            onPlayVideos = { uris -> context.startPlayback(uris) },
            onFolderClick = navController::navigateToMediaPickerScreen,
            onSettingsClick = navController::navigateToSettings,
            onSearchClick = navController::navigateToSearch,
            onVaultClick = navController::navigateToVault,
        )

        searchScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri -> context.startPlayback(listOf(uri)) },
            onFolderClick = navController::navigateToMediaPickerScreen,
        )

        vaultScreen(
            onNavigateUp = navController::navigateUp,
            // Vault files are served through FileProvider, so read access must be granted at
            // playback time for both PlayerActivity and the (separate) PlayerService component.
            onPlayVideo = { uri -> context.startPlayback(listOf(uri), grantReadPermission = true) },
            onPlayVideos = { uris -> context.startPlayback(uris, grantReadPermission = true) },
        )
    }
}

internal fun Context.startPlayback(uris: List<Uri>, grantReadPermission: Boolean = false) {
    if (grantReadPermission) {
        uris.forEach { grantUriPermission(packageName, it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    val intent = Intent(this, PlayerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = uris.first()
        if (uris.size > 1) putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
        if (grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(intent)
}
