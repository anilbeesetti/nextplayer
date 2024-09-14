package dev.anilbeesetti.nextplayer.core.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = {
        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(220, delayMillis = 90)) + fadeIn(animationSpec = tween(220, delayMillis = 90))
    },
    exitTransition = null,
    popEnterTransition = null,
    popExitTransition = {
        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(animationSpec = tween(220))
    },
    content = content,
)
