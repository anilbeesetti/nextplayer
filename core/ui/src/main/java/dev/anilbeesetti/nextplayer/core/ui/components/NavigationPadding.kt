package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

val LocalNavigationBottomPadding = compositionLocalOf { 0.dp }

// Feed the reservation into scroll content/FAB insets without shrinking the screen background.
@Composable
fun playbackScreenInsets(): WindowInsets = WindowInsets.systemBars
    .union(WindowInsets.displayCutout)
    .union(WindowInsets(bottom = LocalNavigationBottomPadding.current))
