package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerEntry
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToMediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToSearch
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToVault
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.searchEntry
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.vaultEntry
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings

fun EntryProviderScope<NavKey>.mediaNavGraph(
    context: Context,
    backStack: NavBackStack<NavKey>,
) {
    mediaPickerEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onPlayVideo = { uri -> context.startPlayback(uri) },
        onPlayVideos = { uris -> context.startPlayback(uris) },
        onFolderClick = backStack::navigateToMediaPickerScreen,
        onSettingsClick = backStack::navigateToSettings,
        onSearchClick = backStack::navigateToSearch,
        onVaultClick = backStack::navigateToVault,
    )

    searchEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onPlayVideo = { uri -> context.startPlayback(uri) },
        onFolderClick = backStack::navigateToMediaPickerScreen,
    )

    vaultEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        // Vault files are served through FileProvider, so read access must be granted at
        // playback time for both PlayerActivity and the (separate) PlayerService component.
        onPlayVideo = { uri -> context.startPlayback(uri, grantReadPermission = true) },
        onPlayVideos = { uris -> context.startPlayback(uris, grantReadPermission = true) },
    )
}

internal fun Context.startPlayback(uri: Uri, grantReadPermission: Boolean = false) {
    startPlayback(uri = uri, playlist = null, grantReadPermission = grantReadPermission)
}

internal fun Context.startPlayback(uris: List<Uri>, grantReadPermission: Boolean = false) {
    val uri = uris.firstOrNull() ?: return
    startPlayback(uri = uri, playlist = uris, grantReadPermission = grantReadPermission)
}

private fun Context.startPlayback(uri: Uri, playlist: List<Uri>?, grantReadPermission: Boolean) {
    if (grantReadPermission) {
        (playlist ?: listOf(uri)).forEach {
            grantUriPermission(packageName, it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val intent = Intent(this, PlayerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = uri
        playlist?.let { putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(it)) }
        if (grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(intent)
}
