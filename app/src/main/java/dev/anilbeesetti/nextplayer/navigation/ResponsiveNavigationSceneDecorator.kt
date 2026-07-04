package dev.anilbeesetti.nextplayer.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Wraps a [Scene] so that top-level destinations are shown alongside the app's navigation UI: a
 * bottom [NextNavigationBar] on compact-width screens and a side [NextNavigationRail] on
 * medium+-width screens.
 *
 * The nav UI is provided as movable content and marked as a shared element, so it stays put while
 * the destination content animates during a tab switch (rather than sliding with it). Non-top-level
 * destinations (folders, settings, connection editing, browsing) render full-screen with no nav UI,
 * matching the pre-Nav3 behaviour.
 *
 * Adapted from the AndroidX nav3-recipes `navscenedecorator` recipe.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
data class ResponsiveNavigationScene<T : Any>(
    private val scene: Scene<T>,
    private val sharedTransitionScope: SharedTransitionScope,
    private val useNavRail: Boolean,
    private val isTopLevel: (contentKey: Any) -> Boolean,
    private val navBarContent: @Composable () -> Unit,
    private val navRailContent: @Composable () -> Unit,
) : Scene<T> by scene {

    override val key = scene::class to scene.key

    override val content: @Composable () -> Unit = {
        // NavEntry.key is private, so identify the destination by its (public) contentKey, which
        // defaults to the route's toString().
        val currentKey = scene.entries.lastOrNull()?.contentKey
        if (currentKey == null || !isTopLevel(currentKey)) {
            scene.content()
        } else {
            val animatedContentScope = LocalNavAnimatedContentScope.current
            // Only the scene transitioning *in* renders the (movable) nav UI; the outgoing scene
            // just reserves its cached size so the content doesn't jump mid-animation.
            val isMovableContentCaller =
                animatedContentScope.transition.targetState == EnterExitState.Visible

            with(sharedTransitionScope) {
                if (useNavRail) {
                    Row(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .cacheSize(!isMovableContentCaller)
                                .sharedElement(
                                    rememberSharedContentState("nav-rail"),
                                    animatedContentScope,
                                ),
                        ) {
                            if (isMovableContentCaller) navRailContent()
                        }
                        Box(Modifier.weight(1f)) { scene.content() }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { scene.content() }
                        Box(
                            modifier = Modifier
                                .cacheSize(!isMovableContentCaller)
                                .sharedElement(
                                    rememberSharedContentState("nav-bar"),
                                    animatedContentScope,
                                ),
                        ) {
                            if (isMovableContentCaller) navBarContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T : Any> rememberResponsiveNavigationSceneDecoratorStrategy(
    useNavRail: Boolean,
    isTopLevel: (contentKey: Any) -> Boolean,
    sharedTransitionScope: SharedTransitionScope,
    navBar: @Composable () -> Unit,
    navRail: @Composable () -> Unit,
): SceneDecoratorStrategy<T> {
    val currentNavBar by rememberUpdatedState(navBar)
    val currentNavRail by rememberUpdatedState(navRail)
    val currentIsTopLevel by rememberUpdatedState(isTopLevel)

    // A single movable instance of the nav UI is handed between scenes during transitions.
    val movableNavBar = remember { movableContentOf { currentNavBar() } }
    val movableNavRail = remember { movableContentOf { currentNavRail() } }

    return remember(useNavRail, sharedTransitionScope) {
        SceneDecoratorStrategy { scene ->
            ResponsiveNavigationScene(
                scene = scene,
                sharedTransitionScope = sharedTransitionScope,
                useNavRail = useNavRail,
                isTopLevel = { currentIsTopLevel(it) },
                navBarContent = movableNavBar,
                navRailContent = movableNavRail,
            )
        }
    }
}

/**
 * Caches the measured size of the content and optionally reuses it, so an element holding movable
 * content keeps its size while the movable content is rendered elsewhere.
 */
fun Modifier.cacheSize(useCachedSize: Boolean): Modifier = this.then(CacheSizeElement(useCachedSize))

private data class CacheSizeElement(val useCachedSize: Boolean) : ModifierNodeElement<CacheSizeNode>() {
    override fun create() = CacheSizeNode(useCachedSize)

    override fun update(node: CacheSizeNode) {
        node.useCachedSize = useCachedSize
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "cacheSize"
        properties["useCachedSize"] = useCachedSize
    }
}

private class CacheSizeNode(useCachedSize: Boolean) : Modifier.Node(), LayoutModifierNode {

    var useCachedSize: Boolean = useCachedSize
        set(value) {
            if (field != value) {
                field = value
                invalidateMeasurement()
            }
        }

    private var isSizeCached = false
    private var cachedSize: IntSize = IntSize.Zero

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val currentSize = IntSize(placeable.width, placeable.height)
        val size = if (useCachedSize && isSizeCached) cachedSize else currentSize
        cachedSize = size
        isSizeCached = true
        return layout(size.width, size.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
