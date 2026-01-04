package dev.anilbeesetti.nextplayer.core.ui.extensions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding(),
    start = this.calculateStartPadding(LocalLayoutDirection.current) +
        other.calculateStartPadding(LocalLayoutDirection.current),
    end = this.calculateEndPadding(LocalLayoutDirection.current) +
        other.calculateEndPadding(LocalLayoutDirection.current),
)

@Composable
fun PaddingValues.copy(
    top: Dp = this.calculateTopPadding(),
    bottom: Dp = this.calculateBottomPadding(),
    start: Dp = this.calculateStartPadding(LocalLayoutDirection.current),
    end: Dp = this.calculateEndPadding(LocalLayoutDirection.current),
): PaddingValues = PaddingValues(start, top, end, bottom)
