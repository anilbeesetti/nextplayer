package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VaultRoute as VaultRouteComposable
import kotlinx.serialization.Serializable

@Serializable
object VaultRoute

fun NavController.navigateToVault(navOptions: NavOptions? = null) {
    this.navigate(VaultRoute, navOptions)
}

fun NavGraphBuilder.vaultScreen(
    onNavigateUp: () -> Unit,
) {
    composable<VaultRoute> {
        VaultRouteComposable(onNavigateUp = onNavigateUp)
    }
}
