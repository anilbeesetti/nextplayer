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
        onNavigateUp = { backStack.removeLastOrNull() },
        onPlayVideo = { uri -> context.startPlayback(listOf(uri)) },
        onPlayVideos = { uris -> context.startPlayback(uris) },
        onFolderClick = backStack::navigateToMediaPickerScreen,
        onSettingsClick = backStack::navigateToSettings,
        onSearchClick = backStack::navigateToSearch,
        onVaultClick = backStack::navigateToVault,
    )

    searchEntry(
        onNavigateUp = { backStack.removeLastOrNull() },
        onPlayVideo = { uri -> context.startPlayback(listOf(uri)) },
        onFolderClick = backStack::navigateToMediaPickerScreen,
    )

    vaultEntry(
        onNavigateUp = { backStack.removeLastOrNull() },
        // Vault files are served through FileProvider, so read access must be granted at
        // playback time for both PlayerActivity and the (separate) PlayerService component.
        onPlayVideo = { uri -> context.startPlayback(listOf(uri), grantReadPermission = true) },
        onPlayVideos = { uris -> context.startPlayback(uris, grantReadPermission = true) },
    )
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
