package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
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
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onPlayVideos = { uris ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris.first()
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
            onSettingsClick = navController::navigateToSettings,
            onSearchClick = navController::navigateToSearch,
            onVaultClick = navController::navigateToVault,
        )

        searchScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
        )

        vaultScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri ->
                // Grant read access explicitly at playback time. FLAG_GRANT_READ_URI_PERMISSION
                // on the Intent covers PlayerActivity, but ExoPlayer streams via PlayerService
                // (a separate component) — grantUriPermission() covers that too.
                context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            },
            onPlayVideos = { uris ->
                uris.forEach { uri ->
                    context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris.first()
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            },
        )
    }
}