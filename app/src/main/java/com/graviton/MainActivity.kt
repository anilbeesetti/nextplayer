package com.graviton

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.graviton.core.common.service.system.SystemService
import com.graviton.core.media.network.proxy.NetworkStreamingProxy
import com.graviton.core.media.services.MediaOperationsService
import com.graviton.core.model.ThemeConfig
import com.graviton.core.ui.theme.GravitonTheme
import com.graviton.feature.player.PlayerActivity
import com.graviton.navigation.NextNavigationBar
import com.graviton.navigation.NextNavigationRail
import com.graviton.navigation.TopLevelDestination
import com.graviton.navigation.isNavigationBetweenTopLevelDestinations
import com.graviton.navigation.mediaNavGraph
import com.graviton.navigation.networkNavGraph
import com.graviton.navigation.playlistNavGraph
import com.graviton.navigation.rememberResponsiveNavigationSceneDecoratorStrategy
import com.graviton.navigation.rememberTopLevelNavState
import com.graviton.navigation.settingsNavGraph
import com.graviton.settings.navigation.navigateToSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var mediaOperationsService: MediaOperationsService

    @Inject
    lateinit var systemService: SystemService

    @Inject
    lateinit var networkStreamingProxy: NetworkStreamingProxy

    @Inject
    lateinit var imageLoader: ImageLoader

    private val viewModel: MainViewModel by viewModels()

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) networkStreamingProxy.release()
    }

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
            // Coil 3.5 no longer exposes a CompositionLocal ImageLoader. Register the existing
            // decoder-aware loader before any media thumbnails are composed.
            setSingletonImageLoaderFactory { _ -> imageLoader }
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

            val appTheme = (uiState as? MainActivityUiState.Success)?.preferences?.appTheme ?: com.graviton.core.model.AppTheme.SYSTEM
            GravitonTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState),
                appTheme = appTheme,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    val navState = rememberTopLevelNavState()

                    val preferences = (uiState as? MainActivityUiState.Success)?.preferences
                    val sceneDecorator = rememberResponsiveNavigationSceneDecoratorStrategy<NavKey>(
                        isTopLevel = { contentKey -> navState.topLevelContentKeys.contains(contentKey) },
                        showNavigation = { preferences?.showBottomNavigation != false },
                        navBar = { NextNavigationBar(navState, preferences) },
                        navRail = { NextNavigationRail(navState, preferences) },
                    )

                    // The video player opens app settings by relaunching this Activity with a
                    // flag, because :feature:player must not depend on :app for navigation.
                    LaunchedEffect(Unit) {
                        if (intent.getBooleanExtra(PlayerActivity.EXTRA_OPEN_SETTINGS, false)) {
                            intent.removeExtra(PlayerActivity.EXTRA_OPEN_SETTINGS)
                            navState.currentStack.navigateToSettings()
                        }
                    }

                    val mediaStack = navState.backStacks.getValue(TopLevelDestination.MEDIA.route)
                    val playlistStack = navState.backStacks.getValue(TopLevelDestination.PLAYLISTS.route)
                    val networkStack = navState.backStacks.getValue(TopLevelDestination.NETWORK.route)

                    // Media and network entries navigate within their own tab's stack; settings is
                    // shared, so it navigates within whichever tab it was opened from (the current one).
                    val provider = entryProvider {
                        mediaNavGraph(context = this@MainActivity, backStack = mediaStack)
                        playlistNavGraph(context = this@MainActivity, backStack = playlistStack)
                        networkNavGraph(context = this@MainActivity, backStack = networkStack)

                        entry<com.graviton.navigation.MusicRoute> {
                            com.graviton.feature.music.MusicHomeScreen()
                        }

                        settingsNavGraph(backStack = navState.currentStack)
                    }

                    NavDisplay(
                        entries = navState.rememberEntries(provider),
                        onBack = { navState.goBack() },
                        sceneDecoratorStrategies = listOf(sceneDecorator),
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
