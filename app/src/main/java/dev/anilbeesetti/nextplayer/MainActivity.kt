package dev.anilbeesetti.nextplayer

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.components.LocalNavigationBottomPadding
import dev.anilbeesetti.nextplayer.core.ui.components.LocalPlaybackOverlayHiddenSetter
import dev.anilbeesetti.nextplayer.core.ui.components.LocalTopLevelBottomBarVisibleSetter
import dev.anilbeesetti.nextplayer.core.ui.components.LocalTopLevelFabSetter
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabState
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.PlaybackOverlay
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.ACTION_EXPAND_PLAYBACK
import dev.anilbeesetti.nextplayer.navigation.NextNavigationBar
import dev.anilbeesetti.nextplayer.navigation.NextNavigationRail
import dev.anilbeesetti.nextplayer.navigation.TopLevelDestination
import dev.anilbeesetti.nextplayer.navigation.TopLevelNavState
import dev.anilbeesetti.nextplayer.navigation.mediaNavGraph
import dev.anilbeesetti.nextplayer.navigation.moreNavGraph
import dev.anilbeesetti.nextplayer.navigation.navigationTransition
import dev.anilbeesetti.nextplayer.navigation.navigationTransitionModifier
import dev.anilbeesetti.nextplayer.navigation.networkNavGraph
import dev.anilbeesetti.nextplayer.navigation.openPlaylist
import dev.anilbeesetti.nextplayer.navigation.playlistNavGraph
import dev.anilbeesetti.nextplayer.navigation.rememberTopLevelNavState
import dev.anilbeesetti.nextplayer.navigation.settingsNavGraph
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : FragmentActivity() {

    private val mediaOperationsService: MediaOperationsService by inject()

    private val systemService: SystemService by inject()

    private val viewModel: MainViewModel by viewModel()

    private var pendingPlaylistId by mutableStateOf<Long?>(value = null)
    private var darkSystemBars = false
    private var playbackExpansionRequest by mutableStateOf(0L)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_EXPAND_PLAYBACK) PlayerActivity.prepareForExpandedControls()
        if (intent.action != ACTION_EXPAND_PLAYBACK) setIntent(intent)
        pendingPlaylistId = PlaylistShortcut.playlistId(intent = intent)
        if (intent.action ==
            ACTION_EXPAND_PLAYBACK
        ) {
            playbackExpansionRequest =
                android.os.SystemClock.elapsedRealtimeNanos()
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == ACTION_EXPAND_PLAYBACK) PlayerActivity.prepareForExpandedControls()
        if (savedInstanceState == null) {
            pendingPlaylistId = PlaylistShortcut.playlistId(intent = intent)
        }
        playbackExpansionRequest = savedInstanceState?.getLong("playbackExpansionRequest")
            ?: if (intent.action ==
                ACTION_EXPAND_PLAYBACK
            ) {
                android.os.SystemClock.elapsedRealtimeNanos()
            } else {
                0L
            }
        systemService.initialize(activity = this@MainActivity)
        mediaOperationsService.initialize(activity = this@MainActivity)

        installSplashScreen().setKeepOnScreenCondition {
            viewModel.state.value is MainActivityUiState.Loading
        }

        darkSystemBars = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
                detectDarkMode = { darkSystemBars },
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
                detectDarkMode = { darkSystemBars },
            ),
        )

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val shouldUseDarkTheme = shouldUseDarkTheme(state = state)

            LaunchedEffect(key1 = shouldUseDarkTheme) {
                darkSystemBars = shouldUseDarkTheme
                WindowCompat.getInsetsController(
                    window,
                    window.decorView,
                ).apply {
                    isAppearanceLightStatusBars = !shouldUseDarkTheme
                    isAppearanceLightNavigationBars = !shouldUseDarkTheme
                }
            }

            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(state = state),
                dynamicColor = shouldUseDynamicTheming(state = state),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    val navState = rememberTopLevelNavState()

                    LaunchedEffect(key1 = pendingPlaylistId) {
                        pendingPlaylistId?.let { playlistId ->
                            navState.openPlaylist(playlistId = playlistId)
                            pendingPlaylistId = null
                        }
                    }

                    val mediaStack = navState.backStacks.getValue(TopLevelDestination.MEDIA.route)
                    val playlistStack = navState.backStacks.getValue(
                        TopLevelDestination.PLAYLISTS.route,
                    )
                    val networkStack = navState.backStacks.getValue(
                        TopLevelDestination.NETWORK.route,
                    )
                    val moreStack = navState.backStacks.getValue(TopLevelDestination.MORE.route)

                    val provider = entryProvider {
                        mediaNavGraph(
                            context = this@MainActivity,
                            backStack = mediaStack,
                        )
                        playlistNavGraph(
                            context = this@MainActivity,
                            backStack = playlistStack,
                        )
                        networkNavGraph(
                            context = this@MainActivity,
                            backStack = networkStack,
                        )
                        moreNavGraph(
                            context = this@MainActivity,
                            backStack = moreStack,
                        )
                        settingsNavGraph(backStack = navState.currentStack)
                    }

                    val topLevelFabStates =
                        remember { mutableStateMapOf<String, TopLevelFabState>() }
                    var showTopLevelBottomBar by remember { mutableStateOf(value = true) }
                    NavigationLayout(
                        state = navState,
                        fabStates = topLevelFabStates,
                        showBottomBar = showTopLevelBottomBar,
                        expansionRequest = playbackExpansionRequest,
                    ) { layoutPaddingValues ->
                        val railPadding = layoutPaddingValues.calculateStartPadding(
                            LocalLayoutDirection.current,
                        )
                        val currentRailPadding = rememberUpdatedState(railPadding)
                        val navigationInsetsDecorator = remember(navState) {
                            NavEntryDecorator<NavKey> { entry ->
                                val topLevel = navState.topLevelContentKeys.contains(
                                    entry.contentKey,
                                )
                                // Reserve space per entry without resizing the animated display.
                                Box(
                                    navState.navigationTransitionModifier(entry).thenIf(
                                        topLevel,
                                    ) {
                                        padding(start = currentRailPadding.value)
                                            .consumeWindowInsets(
                                                WindowInsets.displayCutout.only(
                                                    WindowInsetsSides.Start,
                                                ),
                                            )
                                    },
                                ) {
                                    entry.Content()
                                }
                            }
                        }
                        CompositionLocalProvider(
                            LocalNavigationBottomPadding provides
                                layoutPaddingValues.calculateBottomPadding(),
                            LocalTopLevelBottomBarVisibleSetter provides
                                { showTopLevelBottomBar = it },
                            LocalTopLevelFabSetter provides { key, state ->
                                if (state == null) {
                                    topLevelFabStates.remove(key = key)
                                } else {
                                    topLevelFabStates[key] = state
                                }
                            },
                        ) {
                            NavDisplay(
                                entries = navState.rememberEntries(
                                    entryProvider = provider,
                                    entryDecorator = navigationInsetsDecorator,
                                ),
                                onBack = { navState.goBack() },
                                transitionSpec = {
                                    navState.navigationTransition(
                                        initialState = initialState,
                                        targetState = targetState,
                                    )
                                },
                                popTransitionSpec = {
                                    navState.navigationTransition(
                                        initialState = initialState,
                                        targetState = targetState,
                                    )
                                },
                                predictivePopTransitionSpec = {
                                    navState.navigationTransition(
                                        initialState = initialState,
                                        targetState = targetState,
                                        isPredictive = true,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(
            "playbackExpansionRequest",
            playbackExpansionRequest,
        )
        super.onSaveInstanceState(outState)
    }
}

@Composable
fun NavigationLayout(
    modifier: Modifier = Modifier,
    state: TopLevelNavState,
    fabStates: SnapshotStateMap<String, TopLevelFabState>,
    showBottomBar: Boolean,
    expansionRequest: Long = 0,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
    content: @Composable (PaddingValues) -> Unit,
) {
    val isTv = LocalContext.current.isTelevision
    val density = LocalDensity.current
    var railWidth by remember(density) { mutableStateOf(0.dp) }
    val contentFocusRequester = remember { FocusRequester() }
    val fabFocusRequester = remember { FocusRequester() }
    val miniPlayerFocusRequester = remember { FocusRequester() }
    var miniPlayerVisible by remember { mutableStateOf(false) }
    var playerExpanded by remember { mutableStateOf(false) }
    var compactHeight by remember { mutableStateOf(0.dp) }
    var navigationHeight by remember { mutableStateOf(0.dp) }
    val hiddenOwners = remember { mutableStateMapOf<Any, Boolean>() }
    val setOverlayHidden = remember {
        { owner: Any, hidden: Boolean ->
            if (hidden) hiddenOwners[owner] = true else hiddenOwners.remove(owner)
            Unit
        }
    }
    val overlayHidden = hiddenOwners.isNotEmpty()
    val showNavRail = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
    val showNavigation =
        state.currentStack.lastOrNull()?.let { state.topLevelContentKeys.contains(element = it) } ==
            true
    val selectedFabState = state.destinations[state.selectedIndex].fabKey?.let(
        block = fabStates::get,
    )
    var displayedFabState by remember { mutableStateOf<TopLevelFabState?>(value = null) }
    LaunchedEffect(key1 = selectedFabState) { selectedFabState?.let { displayedFabState = it } }

    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationExtent by animateDpAsState(
        if (showNavigation && showBottomBar) navigationHeight else systemBottom,
        tween(220),
        label = "playback_navigation_extent",
    )
    val railExtent by animateDpAsState(
        if (showNavRail && showNavigation) railWidth else 0.dp,
        tween(220),
        label = "playback_rail_extent",
    )
    val navigationVisibility = (
        (navigationExtent - systemBottom) /
            (navigationHeight - systemBottom).coerceAtLeast(1.dp)
        ).coerceIn(
        0f,
        1f,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.thenIf(playerExpanded) {
                semantics { hideFromAccessibility() }.focusProperties {
                    onEnter = { cancelFocusChange() }
                }.focusGroup()
            },
            bottomBar = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(compactHeight))
                    Box(
                        Modifier.height(navigationExtent)
                            .clip(TopOverflowShape(NavigationShadowHeadroom * navigationVisibility))
                            .graphicsLayer { alpha = navigationVisibility },
                    ) {
                        Box(
                            Modifier.wrapContentHeight(
                                Alignment.Bottom,
                                unbounded = true,
                            )
                                .onSizeChanged {
                                    navigationHeight =
                                        with(density) { it.height.toDp() }
                                }
                                .thenIf(!showNavigation || !showBottomBar) {
                                    semantics { hideFromAccessibility() }
                                        .focusProperties { onEnter = { cancelFocusChange() } }
                                },
                        ) {
                            NextNavigationBar(
                                state = state,
                                fabState = displayedFabState,
                                contentFocusRequester = contentFocusRequester,
                                fabFocusRequester = fabFocusRequester,
                                miniPlayerFocusRequester = miniPlayerFocusRequester.takeIf {
                                    miniPlayerVisible
                                },
                                showFabOnly = showNavRail,
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(0.dp),
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .thenIf(condition = isTv) {
                            // Full-height content groups overlap these controls.
                            // Route focus explicitly between content and the controls.
                            focusRequester(focusRequester = contentFocusRequester)
                                .focusProperties {
                                    onExit = {
                                        if (requestedFocusDirection == FocusDirection.Down &&
                                            (
                                                miniPlayerVisible ||
                                                    (
                                                        showNavigation && showBottomBar &&
                                                            displayedFabState != null
                                                        )
                                                )
                                        ) {
                                            if (miniPlayerVisible) {
                                                miniPlayerFocusRequester.requestFocus()
                                            } else {
                                                fabFocusRequester.requestFocus()
                                            }
                                        }
                                    }
                                }
                                .focusGroup()
                        },
                ) {
                    CompositionLocalProvider(
                        LocalPlaybackOverlayHiddenSetter provides setOverlayHidden,
                    ) {
                        content(
                            PaddingValues(
                                start = railExtent,
                                bottom = it.calculateBottomPadding(),
                            ),
                        )
                    }
                }
            },
        )
        if (showNavRail) {
            Box(Modifier.width(railExtent).fillMaxHeight().clipToBounds()) {
                NextNavigationRail(
                    state = state,
                    modifier = Modifier.wrapContentWidth(
                        Alignment.Start,
                        unbounded = true,
                    )
                        .onSizeChanged { railWidth = with(density) { it.width.toDp() } }
                        .thenIf(!showNavigation || playerExpanded) {
                            semantics { hideFromAccessibility() }
                                .focusProperties { onEnter = { cancelFocusChange() } }
                        },
                )
            }
        }
        PlaybackOverlay(
            modifier = Modifier.fillMaxSize(),
            compactBottom = navigationExtent,
            compactStart = railExtent,
            hidden = overlayHidden,
            expansionRequest = expansionRequest,
            focusRequester = miniPlayerFocusRequester,
            compactModifier = Modifier.focusProperties {
                if (isTv) {
                    up = contentFocusRequester
                    if (showNavigation && showBottomBar &&
                        displayedFabState != null
                    ) {
                        down = fabFocusRequester
                    }
                }
            },
            onCompactHeightChanged = { compactHeight = it },
            onVisibilityChanged = { miniPlayerVisible = it },
            onExpandedChanged = { playerExpanded = it },
        )
    }
}

/**
 * Returns `true` if dark theme should be used, as a function of the [state] and the
 * current system context.
 */
@Composable
fun shouldUseDarkTheme(state: MainActivityUiState): Boolean = when (state) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()

    is MainActivityUiState.Success -> when (state.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseHighContrastDarkTheme(state: MainActivityUiState): Boolean = when (state) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> state.preferences.useHighContrastDarkTheme
}

/**
 * Returns `true` if the dynamic color is disabled, as a function of the [state].
 */
@Composable
fun shouldUseDynamicTheming(state: MainActivityUiState): Boolean = when (state) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> state.preferences.useDynamicColors
}

private val NavigationShadowHeadroom = 16.dp

/** Clips like a rectangle but leaves [overflow] above it, so a child's shadow is not cut off. */
private class TopOverflowShape(private val overflow: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(
        Rect(
            0f,
            -with(density) { overflow.toPx() },
            size.width,
            size.height,
        ),
    )
}
