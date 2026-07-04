package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VaultRoute
import kotlinx.serialization.Serializable

@Serializable
object VaultRoute : NavKey

fun NavBackStack<NavKey>.navigateToVault() {
    add(VaultRoute)
}

fun EntryProviderScope<NavKey>.vaultEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onPlayVideos: (uris: List<Uri>) -> Unit,
) {
    entry<VaultRoute> {
        VaultRoute(
            onPlayVideo = onPlayVideo,
            onPlayVideos = onPlayVideos,
            onNavigateUp = onNavigateUp,
        )
    }
}
