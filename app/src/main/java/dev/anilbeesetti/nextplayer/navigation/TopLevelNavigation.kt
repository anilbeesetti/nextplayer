package dev.anilbeesetti.nextplayer.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.network.navigation.NetworkRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.MediaPickerRoute

/**
 * Top-level destinations shown in the bottom bar / nav rail. The first entry is the start (exit)
 * destination — pressing back from any other tab returns here before leaving the app.
 */
enum class TopLevelDestination(
    val route: NavKey,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    MEDIA(MediaPickerRoute(), NextIcons.Home, R.string.home),
    NETWORK(NetworkRoute, NextIcons.Network, R.string.network),
}

@Composable
fun rememberTopLevelNavState(): TopLevelNavState {
    val destinations = TopLevelDestination.entries
    // Each tab keeps its own back stack; rememberNavBackStack persists it across config change and
    // process death.
    val backStacks = destinations.associate { dest -> dest.route to rememberNavBackStack(dest.route) }
    val selectedIndex = rememberSaveable { mutableIntStateOf(0) }
    return remember(backStacks, selectedIndex) {
        TopLevelNavState(destinations, backStacks, selectedIndex)
    }
}

/**
 * Holds the per-tab back stacks and the currently selected tab, and flattens them into the single
 * list of entries that [androidx.navigation3.ui.NavDisplay] renders.
 *
 * The start tab's stack is always kept at the base, so a non-start tab is displayed *on top* of it;
 * this makes back navigation from a secondary tab fall through to the start tab.
 */
@Stable
class TopLevelNavState(
    val destinations: List<TopLevelDestination>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
    private val selectedIndexState: MutableIntState,
) {
    var selectedIndex by selectedIndexState
        private set

    private val startRoute: NavKey get() = destinations.first().route

    val topLevelRoute: NavKey get() = destinations[selectedIndex].route

    /** The back stack of the currently selected tab — navigation targets are added here. */
    val currentStack: NavBackStack<NavKey> get() = backStacks.getValue(topLevelRoute)

    private val stacksInUse: List<NavKey>
        get() = if (selectedIndex == 0) listOf(startRoute) else listOf(startRoute, topLevelRoute)

    /**
     * The [androidx.navigation3.runtime.NavEntry.contentKey]s of the top-level destinations. Used to
     * decide whether a rendered scene should show the nav bar/rail. `contentKey` defaults to the
     * route's `toString()`, matching how the entries are created.
     */
    val topLevelContentKeys: Set<Any> = destinations.map { it.route.toString() }.toSet()

    fun switchTo(route: NavKey) {
        val index = destinations.indexOfFirst { it.route == route }
        if (index >= 0) selectedIndex = index
    }

    fun goBack() {
        val stack = currentStack
        when {
            stack.size > 1 -> stack.removeLastOrNull()
            selectedIndex != 0 -> selectedIndex = 0
        }
    }

    @Composable
    fun rememberEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): SnapshotStateList<NavEntry<NavKey>> {
        val decoratedByRoute = LinkedHashMap<NavKey, List<NavEntry<NavKey>>>()
        for (dest in destinations) {
            decoratedByRoute[dest.route] = rememberDecoratedNavEntries(
                backStack = backStacks.getValue(dest.route),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
        return stacksInUse.flatMap { decoratedByRoute.getValue(it) }.toMutableStateList()
    }
}

fun TopLevelNavState.isNavigationBetweenTopLevelDestinations(initialState: Scene<NavKey>, targetState: Scene<NavKey>): Boolean =
    topLevelContentKeys.run { contains(initialState.entries.lastOrNull()?.contentKey) && contains(targetState.entries.lastOrNull()?.contentKey) }

@Composable
fun NextNavigationBar(state: TopLevelNavState) {
    NavigationBar {
        state.destinations.forEach { dest ->
            NavigationBarItem(
                selected = state.topLevelRoute == dest.route,
                onClick = { state.switchTo(dest.route) },
                icon = { Icon(imageVector = dest.icon, contentDescription = null) },
                label = { Text(text = stringResource(dest.labelRes)) },
                modifier = Modifier.tvFocusRing(shape = RoundedCornerShape(24.dp)),
            )
        }
    }
}

@Composable
fun NextNavigationRail(state: TopLevelNavState) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            state.destinations.forEach { dest ->
                val selected = state.topLevelRoute == dest.route
                Box(
                    Modifier
                        .tvFocusRing(shape = RoundedCornerShape(99.dp))
                        .clip(CircleShape)
                        .selectable(
                            selected = state.topLevelRoute == dest.route,
                            onClick = { state.switchTo(dest.route) },
                            role = Role.Tab,
                        )
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                    propagateMinConstraints = true,
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
