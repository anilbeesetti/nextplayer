package com.graviton.navigation

import android.content.Context
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.feature.network.navigation.addConnectionEntry
import com.graviton.feature.network.navigation.navigateToAddConnection
import com.graviton.feature.network.navigation.navigateToNetworkBrowse
import com.graviton.feature.network.navigation.networkBrowseEntry
import com.graviton.feature.network.navigation.networkEntry
import com.graviton.settings.navigation.navigateToSettings

fun EntryProviderScope<NavKey>.networkNavGraph(
    context: Context,
    backStack: NavBackStack<NavKey>,
) {
    networkEntry(
        onAddConnection = { backStack.navigateToAddConnection() },
        onEditConnection = { id -> backStack.navigateToAddConnection(id) },
        onOpenConnection = { id -> backStack.navigateToNetworkBrowse(id) },
        onSettingsClick = backStack::navigateToSettings,
    )

    addConnectionEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )

    networkBrowseEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onPlayVideo = { uri -> context.startPlayback(uri) },
        onNavigateToFolder = { id, path -> backStack.navigateToNetworkBrowse(id, path) },
    )
}
