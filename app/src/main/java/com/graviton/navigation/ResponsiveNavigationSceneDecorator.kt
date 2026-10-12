package com.graviton.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.window.core.layout.WindowSizeClass
import com.graviton.feature.music.MiniPlayerReopenButton
import com.graviton.feature.music.MiniPlayerVisibilityState
import com.graviton.feature.music.NowPlayingMiniBar
import com.graviton.feature.music.rememberMiniPlayerVisibilityState

/**
 * Wraps a [Scene] so that top-level destinations are shown alongside the app's navigation UI: a
 * bottom [NextNavigationBar] on compact-width screens and a side [NextNavigationRail] on
 * medium+-width screens. Non-top-level destinations (folders, settings, connection editing,
 * browsing) render full-screen with no nav UI.
 *
 * The nav UI is rendered directly (not as movable/shared content): tab switches use a cross-fade, so
 * the identical bar/rail in both scenes fades in place. Keeping it a plain composable — rather than a
 * shared element in a transition overlay — is what makes D-pad focus traversal work on Android TV.
 *
 * Adapted from the AndroidX nav3-recipes `navscenedecorator` recipe.
 */
// Intentionally NOT a `data class`. A generated equals/hashCode would include the lambda parameters,
// and the decorator strategy hands each decoration fresh lambda instances, so two wrappers of the
// same destination would never be equal. NavDisplay compares scenes by value equality to drive its
// predictive-back handshake (e.g. `predictiveBackCompleted = transition.targetState == scene`); if
// that comparison is always false, a completed system/predictive back is mistaken for a cancelled
// one and NavDisplay animates back toward the already-popped entry, leaving the exiting screen blank.
// Equality therefore keys off the wrapped [scene] (and window size) only — the lambdas are ignored.
class ResponsiveNavigationScene<T : Any>(
    private val scene: Scene<T>,
    private val windowSizeClass: WindowSizeClass,
    private val miniPlayerVisibility: MiniPlayerVisibilityState,
    private val isTopLevel: (contentKey: Any) -> Boolean,
    private val navBarContent: @Composable () -> Unit,
    private val navRailContent: @Composable () -> Unit,
    private val showNavigation: () -> Boolean,
) : Scene<T> by scene {

    override val key = scene::class to scene.key

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResponsiveNavigationScene<*>) return false
        return scene == other.scene && windowSizeClass == other.windowSizeClass
    }

    override fun hashCode(): Int = 31 * scene.hashCode() + windowSizeClass.hashCode()

    override val content: @Composable () -> Unit = {
        val currentKey = scene.entries.lastOrNull()?.contentKey

        if (currentKey == null || !isTopLevel(currentKey)) {
            scene.content()
        } else if (!showNavigation()) {
            Box {
                scene.content()
                NowPlayingMiniBar(
                    visibilityState = miniPlayerVisibility,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                MiniPlayerReopenButton(
                    visibilityState = miniPlayerVisibility,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        } else {
            val navLayoutType = when {
                windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
                    NavigationSuiteType.NavigationRail
                }

                else -> NavigationSuiteType.NavigationBar
            }

            NavigationSuiteScaffoldLayout(
                layoutType = navLayoutType,
                navigationSuite = {
                    when (navLayoutType) {
                        // On phones the mini bar is stacked directly on top of the bar so the two
                        // read as one bottom surface, and the bar's own insets keep both clear of
                        // the system navigation.
                        NavigationSuiteType.NavigationBar -> Column {
                            NowPlayingMiniBar(visibilityState = miniPlayerVisibility)
                            navBarContent()
                        }

                        NavigationSuiteType.NavigationRail -> navRailContent()
                    }
                },
            ) {
                Box(
                    modifier = Modifier.consumeWindowInsets(
                        NavigationRailDefaults.windowInsets.only(
                            when (navLayoutType) {
                                NavigationSuiteType.NavigationBar -> WindowInsetsSides.Bottom
                                NavigationSuiteType.NavigationRail -> WindowInsetsSides.Start
                                else -> WindowInsetsSides.Bottom
                            },
                        ),
                    ),
                ) {
                    scene.content()
                    // With a rail there is no bottom bar to attach to, so the mini bar anchors to
                    // the bottom of the content pane instead.
                    if (navLayoutType == NavigationSuiteType.NavigationRail) {
                        NowPlayingMiniBar(
                            visibilityState = miniPlayerVisibility,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    // The way back to a hidden mini player. On phones it floats above the bottom
                    // bar; with a rail it sits in the corner of the content pane.
                    MiniPlayerReopenButton(
                        visibilityState = miniPlayerVisibility,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 16.dp,
                                bottom = if (navLayoutType == NavigationSuiteType.NavigationBar) 96.dp else 16.dp,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun <T : Any> rememberResponsiveNavigationSceneDecoratorStrategy(
    isTopLevel: (contentKey: Any) -> Boolean,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
    showNavigation: () -> Boolean = { true },
    navBar: @Composable () -> Unit,
    navRail: @Composable () -> Unit,
): SceneDecoratorStrategy<T> {
    val currentNavBar by rememberUpdatedState(navBar)
    val currentNavRail by rememberUpdatedState(navRail)
    val currentIsTopLevel by rememberUpdatedState(isTopLevel)
    val currentShowNavigation by rememberUpdatedState(showNavigation)
    // Owned here rather than by the bar so that hiding it survives navigation, and so that every
    // layout branch (bar, rail, no navigation) shares the same hidden/visible state.
    val miniPlayerVisibility = rememberMiniPlayerVisibilityState()

    return remember(windowSizeClass) {
        SceneDecoratorStrategy { scene ->
            ResponsiveNavigationScene(
                scene = scene,
                windowSizeClass = windowSizeClass,
                miniPlayerVisibility = miniPlayerVisibility,
                isTopLevel = { currentIsTopLevel(it) },
                navBarContent = { currentNavBar() },
                navRailContent = { currentNavRail() },
                showNavigation = { currentShowNavigation() },
            )
        }
    }
}
