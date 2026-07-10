package dev.anilbeesetti.nextplayer.feature.iptv.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.feature.iptv.IptvScreenRoute
import kotlinx.serialization.Serializable

@Serializable
object IptvRoute : NavKey

fun NavBackStack<NavKey>.navigateToIptv() {
    add(IptvRoute)
}

fun EntryProviderScope<NavKey>.iptvEntry(
    onNavigateUp: () -> Unit,
    onPlayChannel: (IptvChannel) -> Unit,
) {
    entry<IptvRoute> {
        IptvScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayChannel = onPlayChannel,
        )
    }
}
