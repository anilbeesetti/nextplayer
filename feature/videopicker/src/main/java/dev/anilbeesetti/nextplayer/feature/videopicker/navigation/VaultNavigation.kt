package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VaultRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VaultViewModel
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
            output = VaultViewModel.Output(
                playVideo = onPlayVideo,
                playVideos = onPlayVideos,
                navigateUp = onNavigateUp,
            ),
        )
    }
}
