package dev.anilbeesetti.nextplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.components.LocalNavigationBottomPadding
import dev.anilbeesetti.nextplayer.core.ui.components.LocalTopLevelBottomBarVisibleSetter
import dev.anilbeesetti.nextplayer.core.ui.components.LocalTopLevelFabSetter
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabState
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.navigation.NextNavigationBar
import dev.anilbeesetti.nextplayer.navigation.NextNavigationRail
import dev.anilbeesetti.nextplayer.navigation.TopLevelDestination
import dev.anilbeesetti.nextplayer.navigation.TopLevelNavState
import dev.anilbeesetti.nextplayer.navigation.isNavigationBetweenTopLevelDestinations
import dev.anilbeesetti.nextplayer.navigation.mediaNavGraph
import dev.anilbeesetti.nextplayer.navigation.moreNavGraph
import dev.anilbeesetti.nextplayer.navigation.networkNavGraph
import dev.anilbeesetti.nextplayer.navigation.playlistNavGraph
import dev.anilbeesetti.nextplayer.navigation.rememberTopLevelNavState
import dev.anilbeesetti.nextplayer.navigation.settingsNavGraph
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var mediaOperationsService: MediaOperationsService

    @Inject
    lateinit var systemService: SystemService

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemService.initialize(this@MainActivity)
        mediaOperationsService.initialize(this@MainActivity)
        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }

        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(uiState = uiState)

            LaunchedEffect(shouldUseDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                )
            }

            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    val navState = rememberTopLevelNavState()

                    val mediaStack = navState.backStacks.getValue(TopLevelDestination.MEDIA.route)
                    val playlistStack = navState.backStacks.getValue(TopLevelDestination.PLAYLISTS.route)
                    val networkStack = navState.backStacks.getValue(TopLevelDestination.NETWORK.route)
                    val moreStack = navState.backStacks.getValue(TopLevelDestination.MORE.route)

                    val provider = entryProvider {
                        mediaNavGraph(context = this@MainActivity, backStack = mediaStack)
                        playlistNavGraph(context = this@MainActivity, backStack = playlistStack)
                        networkNavGraph(context = this@MainActivity, backStack = networkStack)
                        moreNavGraph(context = this@MainActivity, backStack = moreStack)
                        settingsNavGraph(backStack = navState.currentStack)
                    }

                    val topLevelFabStates = remember { mutableStateMapOf<String, TopLevelFabState>() }
                    var showTopLevelBottomBar by remember { mutableStateOf(true) }
                    NavigationLayout(
                        state = navState,
                        fabStates = topLevelFabStates,
                        showBottomBar = showTopLevelBottomBar,
                    ) { layoutPaddingValues ->
                        CompositionLocalProvider(
                            LocalNavigationBottomPadding provides layoutPaddingValues.calculateBottomPadding(),
                            LocalTopLevelBottomBarVisibleSetter provides { showTopLevelBottomBar = it },
                            LocalTopLevelFabSetter provides { key, state ->
                                if (state == null) {
                                    topLevelFabStates.remove(key)
                                } else {
                                    topLevelFabStates[key] = state
                                }
                            },
                        ) {
                            NavDisplay(
                                entries = navState.rememberEntries(provider),
                                onBack = { navState.goBack() },
                                transitionSpec = {
                                    if (navState.isNavigationBetweenTopLevelDestinations(initialState, targetState)) {
                                        fadeIn(
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        ) togetherWith fadeOut(
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    } else {
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                        ) togetherWith slideOutHorizontally(
                                            targetOffsetX = { fullOffset -> -(fullOffset * 0.3f).toInt() },
                                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                        )
                                    }
                                },
                                popTransitionSpec = {
                                    if (navState.isNavigationBetweenTopLevelDestinations(initialState, targetState)) {
                                        fadeIn(
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        ) togetherWith fadeOut(
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    } else {
                                        slideInHorizontally(
                                            initialOffsetX = { fullOffset -> -(fullOffset * 0.3f).toInt() },
                                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                        ) togetherWith slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                        )
                                    }
                                },
                                predictivePopTransitionSpec = {
                                    if (navState.isNavigationBetweenTopLevelDestinations(initialState, targetState)) {
                                        fadeIn(
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        ) togetherWith fadeOut(
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    } else {
                                        slideInHorizontally(
                                            initialOffsetX = { fullOffset -> -(fullOffset * 0.3f).toInt() },
                                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                        ) togetherWith slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationLayout(
    modifier: Modifier = Modifier,
    state: TopLevelNavState,
    fabStates: SnapshotStateMap<String, TopLevelFabState>,
    showBottomBar: Boolean,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
    content: @Composable (PaddingValues) -> Unit,
) {
    val isTv = LocalContext.current.isTelevision
    val contentFocusRequester = remember { FocusRequester() }
    val fabFocusRequester = remember { FocusRequester() }
    val showNavRail = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val showNavigation = state.currentStack.lastOrNull()?.let { state.topLevelContentKeys.contains(it) } == true
    val selectedFabState = state.destinations[state.selectedIndex].fabKey?.let(fabStates::get)
    var displayedFabState by remember { mutableStateOf<TopLevelFabState?>(null) }
    LaunchedEffect(selectedFabState) {
        selectedFabState?.let { displayedFabState = it }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showNavRail && showNavigation,
        ) {
            NextNavigationRail(state = state)
        }
        Scaffold(
            modifier = modifier,
            bottomBar = {
                AnimatedVisibility(
                    visible = showNavigation && showBottomBar,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                    NextNavigationBar(
                        state = state,
                        fabState = displayedFabState,
                        contentFocusRequester = contentFocusRequester,
                        fabFocusRequester = fabFocusRequester,
                        showFabOnly = showNavRail,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(0.dp),
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .thenIf(isTv) {
                            // The FAB sits outside screen content, whose full-height focus groups
                            // overlap it. Enter the content explicitly instead of searching the rail.
                            focusRequester(contentFocusRequester)
                                .focusProperties {
                                    onExit = {
                                        if (requestedFocusDirection == FocusDirection.Down &&
                                            showNavigation && showBottomBar && displayedFabState != null
                                        ) {
                                            fabFocusRequester.requestFocus()
                                        }
                                    }
                                }
                                .focusGroup()
                        }
                        .thenIf(showNavigation) {
                            consumeWindowInsets(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                        },
                ) {
                    content(it)
                }
            },
        )
    }
}

/**
 * Returns `true` if dark theme should be used, as a function of the [uiState] and the
 * current system context.
 */
@Composable
fun shouldUseDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseHighContrastDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useHighContrastDarkTheme
}

/**
 * Returns `true` if the dynamic color is disabled, as a function of the [uiState].
 */
@Composable
fun shouldUseDynamicTheming(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useDynamicColors
}
