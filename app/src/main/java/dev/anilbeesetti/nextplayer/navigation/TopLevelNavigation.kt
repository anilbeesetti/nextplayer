package dev.anilbeesetti.nextplayer.navigation

import android.os.Build
import android.view.RoundedCorner
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
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
    MEDIA(
        MediaPickerRoute(),
        NextIcons.Home,
        R.string.home,
        TopLevelFabKey.MEDIA,
    ),
    PLAYLISTS(
        PlaylistListRoute,
        NextIcons.Playlist,
        R.string.playlists,
        TopLevelFabKey.PLAYLISTS,
    ),
    NETWORK(
        NetworkRoute,
        NextIcons.Network,
        R.string.network,
        TopLevelFabKey.NETWORK,
    ),
    MORE(
        MoreRoute,
        NextIcons.More,
        R.string.more,
        TopLevelFabKey.MORE,
    ),
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
    val selectedIndex = rememberSaveable { mutableIntStateOf(value = 0) }
    return remember(
        backStacks,
        selectedIndex,
    ) {
        TopLevelNavState(
            destinations = destinations,
            backStacks = backStacks,
            selectedIndexState = selectedIndex,
        )
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
    val currentStack: NavBackStack<NavKey> get() = backStacks.getValue(key = topLevelRoute)

    private val stacksInUse: List<NavKey>
        get() = if (selectedIndex == 0) {
            listOf(element = startRoute)
        } else {
            listOf(
                startRoute,
                topLevelRoute,
            )
        }

    /** Raw routes and Navigation 3 content keys for the top-level destinations. */
    val topLevelContentKeys: Set<Any> = destinations.flatMap { dest ->
        listOf(
            dest.route,
            NavEntry(key = dest.route) {}.contentKey,
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
    fun rememberEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
        entryDecorator: NavEntryDecorator<NavKey>,
    ): SnapshotStateList<NavEntry<NavKey>> {
        val decoratedByRoute = LinkedHashMap<NavKey, List<NavEntry<NavKey>>>()
        for (dest in destinations) {
            decoratedByRoute[dest.route] = rememberDecoratedNavEntries(
                backStack = backStacks.getValue(dest.route),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    entryDecorator,
                ),
                entryProvider = entryProvider,
            )
        }
        return stacksInUse.flatMap { decoratedByRoute.getValue(key = it) }.toMutableStateList()
    }
}

fun TopLevelNavState.isNavigationBetweenTopLevelDestinations(
    initialState: Scene<NavKey>,
    targetState: Scene<NavKey>,
): Boolean = topLevelContentKeys.run {
    contains(initialState.entries.lastOrNull()?.contentKey) &&
        contains(targetState.entries.lastOrNull()?.contentKey)
}

// Timings measured from the system activity transition (ColorOS 16 Settings), which apps can't
// borrow directly for in-activity navigation.
private const val PUSH_DURATION_MILLIS = 320
private const val POP_DURATION_MILLIS = 360
private val PushEasing = CubicBezierEasing(
    0.3f,
    0.7f,
    0.5f,
    1f,
)
private val PopEasing = CubicBezierEasing(
    0.3f,
    0.3f,
    0.3f,
    1f,
)
private const val PARALLAX_FRACTION = 0.3f
private const val PREDICTIVE_PARALLAX_FRACTION = 0.25f
private const val MAX_SCRIM_ALPHA = 0.5f

// Quick Back can select the pop spec while the animated scenes still describe the push.
// Keep the direction tied to those scenes so the slide can reverse smoothly.
private fun Scene<NavKey>.isPoppingTo(targetState: Scene<NavKey>): Boolean = previousEntries.any {
    it.contentKey == targetState.entries.lastOrNull()?.contentKey
}

internal fun TopLevelNavState.navigationTransition(
    initialState: Scene<NavKey>,
    targetState: Scene<NavKey>,
    isPredictive: Boolean = false,
): ContentTransform {
    if (isNavigationBetweenTopLevelDestinations(
            initialState = initialState,
            targetState = targetState,
        )
    ) {
        return fadeIn(
            animationSpec = tween(
                durationMillis = 200,
                easing = LinearEasing,
            ),
        ) togetherWith
            fadeOut(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = LinearEasing,
                ),
            )
    }

    val isPop = initialState.isPoppingTo(targetState)
    // Predictive back seeks this transition with the gesture progress, so keep it linear to track
    // the finger.
    val animationSpec = when {
        isPredictive -> tween<IntOffset>(
            durationMillis = POP_DURATION_MILLIS,
            easing = LinearEasing,
        )

        isPop -> tween(
            durationMillis = POP_DURATION_MILLIS,
            easing = PopEasing,
        )

        else -> tween(
            durationMillis = PUSH_DURATION_MILLIS,
            easing = PushEasing,
        )
    }
    val parallax = if (isPredictive) PREDICTIVE_PARALLAX_FRACTION else PARALLAX_FRACTION
    return slideInHorizontally(
        initialOffsetX = { if (isPop) -(it * parallax).toInt() else it },
        animationSpec = animationSpec,
    ) togetherWith slideOutHorizontally(
        targetOffsetX = { if (isPop) it else -(it * parallax).toInt() },
        animationSpec = animationSpec,
    )
}

/**
 * Matches the system activity transition: dims the screen underneath a push or pop and clips the
 * screen on top to the display's rounded corners, when the system reports them.
 */
@Composable
internal fun TopLevelNavState.navigationTransitionModifier(entry: NavEntry<NavKey>): Modifier {
    val visibility = LocalNavAnimatedContentScope.current.transition

    @Suppress("UNCHECKED_CAST")
    val scenes = visibility.parentTransition as? Transition<Scene<NavKey>> ?: return Modifier
    val initialState = scenes.currentState
    val targetState = scenes.targetState
    val isPop = initialState.isPoppingTo(targetState)
    val scrimAlpha = visibility.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (isPop) POP_DURATION_MILLIS else PUSH_DURATION_MILLIS,
                easing = LinearEasing,
            )
        },
        label = "navigation_scrim",
    ) { if (it == EnterExitState.Visible) 0f else MAX_SCRIM_ALPHA }
    val cornerShape = rememberDisplayCornerShape()

    if (initialState == targetState ||
        isNavigationBetweenTopLevelDestinations(
            initialState,
            targetState,
        )
    ) {
        return Modifier
    }
    val bottomScene = if (isPop) targetState else initialState
    return if (entry.contentKey == bottomScene.entries.lastOrNull()?.contentKey) {
        Modifier.drawWithContent {
            drawContent()
            drawRect(
                color = Color.Black,
                alpha = scrimAlpha.value,
            )
        }
    } else {
        Modifier.thenIf(cornerShape != null) { clip(cornerShape!!) }
    }
}

@Composable
private fun rememberDisplayCornerShape(): Shape? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val insets = LocalView.current.rootWindowInsets ?: return null
    return remember(insets) {
        fun radius(position: Int) = insets.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
        val topLeft = radius(RoundedCorner.POSITION_TOP_LEFT)
        val topRight = radius(RoundedCorner.POSITION_TOP_RIGHT)
        val bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT)
        val bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT)
        if (topLeft + topRight + bottomRight + bottomLeft == 0f) {
            null
        } else {
            AbsoluteRoundedCornerShape(
                topLeft = topLeft,
                topRight = topRight,
                bottomRight = bottomRight,
                bottomLeft = bottomLeft,
            )
        }
    }
}

@Composable
fun NextNavigationBar(
    state: TopLevelNavState,
    fabState: TopLevelFabState?,
    contentFocusRequester: FocusRequester,
    fabFocusRequester: FocusRequester,
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
            .thenIf(condition = !showFabOnly) { pointerInput(key1 = Unit) {} },
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
                Spacer(Modifier.weight(weight = 1f))
            }

            fabState?.let { fab ->
                FloatingActionButton(
                    onClick = fab.onClick,
                    modifier = Modifier
                        .testTag(tag = "top_level_fab")
                        .focusRequester(focusRequester = fabFocusRequester)
                        .tvFocusRing(shape = MaterialTheme.shapes.large)
                        .focusProperties {
                            if (isTv) {
                                up =
                                    fab.upFocusRequester.takeUnless { it == FocusRequester.Default }
                                        ?: contentFocusRequester
                            }
                        },
                    shape = MaterialTheme.shapes.large,
                ) {
                    AnimatedContent(
                        targetState = fab,
                        transitionSpec = {
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutSlowInEasing,
                                ),
                            ) +
                                scaleIn(
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    initialScale = 0.25f,
                                ) togetherWith
                                fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ) +
                                scaleOut(
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    targetScale = 0.25f,
                                )
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
    val boundsTransform = remember { BoundsTransform { _, _ -> animationSpec } }

    Box(
        modifier = modifier
            .graphicsLayer(
                shadowElevation = with(LocalDensity.current) { 6.dp.toPx() },
                shape = CircleShape,
            )
            .clip(shape = CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
    ) {
        LookaheadScope {
            val pill = remember {
                movableContentOf<BoxScope> { scope ->
                    with(receiver = scope) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .animateBounds(
                                    lookaheadScope = this@LookaheadScope,
                                    boundsTransform = boundsTransform,
                                )
                                .clip(shape = CircleShape)
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
                        modifier = Modifier.zIndex(zIndex = if (isSelected) 0f else 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            pill(this)
                        }
                        Column(
                            Modifier
                                .tvFocusRing(shape = RoundedCornerShape(99.dp))
                                .clip(shape = CircleShape)
                                .selectable(
                                    selected = isSelected,
                                    onClick = { onDestinationClick(destination) },
                                    role = Role.Tab,
                                    indication = null,
                                    interactionSource = null,
                                )
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            CompositionLocalProvider(
                                value = LocalContentColor provides if (isSelected) {
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
fun NextNavigationRail(state: TopLevelNavState, modifier: Modifier = Modifier) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterVertically,
            ),
        ) {
            state.destinations.forEach { dest ->
                val selected = state.topLevelRoute == dest.route
                Box(
                    Modifier
                        .tvFocusRing(shape = RoundedCornerShape(99.dp))
                        .clip(shape = CircleShape)
                        .selectable(
                            selected = state.topLevelRoute == dest.route,
                            onClick = { state.switchTo(dest.route) },
                            role = Role.Tab,
                        )
                        .background(
                            color = if (selected) {
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
                        value = LocalContentColor provides if (selected) {
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
