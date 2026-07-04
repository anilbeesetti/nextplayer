package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extra bottom padding that scrollable content should add so its last items can scroll clear of the
 * floating bottom navigation bar (letting content scroll *beneath* the translucent bar rather than
 * being clipped above it). It is `0.dp` on screens where the bar is hidden.
 */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }
