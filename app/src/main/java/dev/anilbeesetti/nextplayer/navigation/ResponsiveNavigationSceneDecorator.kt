package dev.anilbeesetti.nextplayer.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.window.core.layout.WindowSizeClass

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
data class ResponsiveNavigationScene<T : Any>(
    private val scene: Scene<T>,
    private val windowSizeClass: WindowSizeClass,
    private val isTopLevel: (contentKey: Any) -> Boolean,
    private val navBarContent: @Composable () -> Unit,
    private val navRailContent: @Composable () -> Unit,
) : Scene<T> by scene {

    override val key = scene::class to scene.key

    override val content: @Composable () -> Unit = {
        val currentKey = scene.entries.lastOrNull()?.contentKey
        when {
            currentKey == null || !isTopLevel(currentKey) -> scene.content()

            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
                Row(Modifier.fillMaxSize()) {
                    navRailContent()
                    Box(
                        Modifier
                            .weight(1f)
                            .consumeWindowInsets(NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)),
                    ) {
                        scene.content()
                    }
                }
            }

            else -> {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .consumeWindowInsets(NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)),
                    ) {
                        scene.content()
                    }
                    navBarContent()
                }
            }
        }
    }
}

@Composable
fun <T : Any> rememberResponsiveNavigationSceneDecoratorStrategy(
    isTopLevel: (contentKey: Any) -> Boolean,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    navBar: @Composable () -> Unit,
    navRail: @Composable () -> Unit,
): SceneDecoratorStrategy<T> {
    val currentNavBar by rememberUpdatedState(navBar)
    val currentNavRail by rememberUpdatedState(navRail)
    val currentIsTopLevel by rememberUpdatedState(isTopLevel)

    return remember(windowSizeClass) {
        SceneDecoratorStrategy { scene ->
            ResponsiveNavigationScene(
                scene = scene,
                windowSizeClass = windowSizeClass,
                isTopLevel = { currentIsTopLevel(it) },
                navBarContent = { currentNavBar() },
                navRailContent = { currentNavRail() },
            )
        }
    }
}
