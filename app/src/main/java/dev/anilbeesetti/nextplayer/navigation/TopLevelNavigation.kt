package dev.anilbeesetti.nextplayer.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabState
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.more.navigation.MoreRoute
import dev.anilbeesetti.nextplayer.feature.network.navigation.NetworkRoute
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.PlaylistListRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.MediaPickerRoute

/**
 * Top-level destinations shown in the bottom bar / nav rail. The first entry is the start (exit)
 * destination — pressing back from any other tab returns here before leaving the app.
 */
enum class TopLevelDestination(
    val route: NavKey,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val fabKey: String? = null,
) {
    MEDIA(MediaPickerRoute(), NextIcons.Home, R.string.home, TopLevelFabKey.MEDIA),
    PLAYLISTS(PlaylistListRoute, NextIcons.Playlist, R.string.playlists, TopLevelFabKey.PLAYLISTS),
    NETWORK(NetworkRoute, NextIcons.Network, R.string.network, TopLevelFabKey.NETWORK),
    MORE(MoreRoute, NextIcons.More, R.string.more, TopLevelFabKey.MORE),
}

@Composable
fun rememberTopLevelNavState(): TopLevelNavState {
    val destinations = TopLevelDestination.entries
    // Each tab keeps its own back stack; rememberNavBackStack persists it across config change and
    // process death.
    val backStacks = destinations.associate { dest ->
        val backStack = rememberNavBackStack(dest.route)
        backStack.ensureRoot(dest.route)
        dest.route to backStack
    }
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
    val topLevelContentKeys: Set<Any> = destinations.flatMap { dest ->
        listOf(
            dest.route,
            dest.route.toString(),
            Pair("${dest.route}", "${dest.route::class}"),
        )
    }.toSet()

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
fun NextNavigationBar(
    state: TopLevelNavState,
    fabState: TopLevelFabState?,
    showFabOnly: Boolean = false,
) {
    val isTv = LocalContext.current.isTelevision
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .thenIf(condition = !showFabOnly) {
                background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
            }
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(bottom = 12.dp)
            .padding(horizontal = 16.dp)
            .thenIf(condition = !showFabOnly) {
                pointerInput(Unit) {}
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!showFabOnly) {
                NavigationView(
                    destinations = state.destinations,
                    isSelected = { it.route == state.topLevelRoute },
                    onDestinationClick = { state.switchTo(it.route) },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            fabState?.let { fab ->
                FloatingActionButton(
                    onClick = fab.onClick,
                    modifier = Modifier
                        .testTag("top_level_fab")
                        .tvFocusRing(shape = MaterialTheme.shapes.large)
                        .focusProperties { if (isTv) up = fab.upFocusRequester },
                    shape = MaterialTheme.shapes.large,
                ) {
                    AnimatedContent(
                        targetState = fab,
                        transitionSpec = {
                            fadeIn(tween(250, easing = FastOutSlowInEasing)) +
                                scaleIn(tween(250, easing = FastOutSlowInEasing), initialScale = 0.25f) togetherWith
                                fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                                scaleOut(tween(250, easing = FastOutSlowInEasing), targetScale = 0.25f)
                        },
                        label = "fab_icon_swap",
                    ) { fab ->
                        Icon(
                            imageVector = fab.icon,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationView(
    modifier: Modifier = Modifier,
    destinations: List<TopLevelDestination>,
    isSelected: (TopLevelDestination) -> Boolean,
    onDestinationClick: (TopLevelDestination) -> Unit,
) {
    val animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val boundsTransform = remember {
        BoundsTransform { _, _ -> animationSpec }
    }

    Box(
        modifier = modifier
            .graphicsLayer(
                shadowElevation = with(LocalDensity.current) { 6.dp.toPx() },
                shape = CircleShape,
            )
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
    ) {
        LookaheadScope {
            val pill = remember {
                movableContentOf<BoxScope> { scope ->
                    with(scope) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .animateBounds(
                                    lookaheadScope = this@LookaheadScope,
                                    boundsTransform = boundsTransform,
                                )
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                destinations.forEach { destination ->
                    val isSelected = isSelected(destination)
                    Box(
                        modifier = Modifier.zIndex(if (isSelected) 0f else 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            pill(this)
                        }
                        Column(
                            Modifier
                                .tvFocusRing(shape = RoundedCornerShape(99.dp))
                                .clip(CircleShape)
                                .selectable(
                                    selected = isSelected,
                                    onClick = { onDestinationClick(destination) },
                                    role = Role.Tab,
                                    indication = null,
                                    interactionSource = null,
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
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
                            },
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                    propagateMinConstraints = true,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
