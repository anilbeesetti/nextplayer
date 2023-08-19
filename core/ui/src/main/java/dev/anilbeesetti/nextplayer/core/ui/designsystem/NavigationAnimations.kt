package dev.anilbeesetti.nextplayer.core.ui.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object NavigationAnimations {

    val slideEnter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300))
    val slideExit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300))

}