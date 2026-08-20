package com.graviton.settings.extensions

import androidx.compose.runtime.Composable
import com.graviton.core.model.AppTheme

@Composable
fun AppTheme.name(): String {
    return when (this) {
        AppTheme.SYSTEM -> "System / Dynamic"
        AppTheme.AMOLED -> "AMOLED"
        AppTheme.OCEAN -> "Ocean"
        AppTheme.BLUE -> "Blue"
        AppTheme.PURPLE -> "Purple"
        AppTheme.GREEN -> "Green"
        AppTheme.RED -> "Red"
        AppTheme.ORANGE -> "Orange"
        AppTheme.PINK -> "Pink"
        AppTheme.CYAN -> "Cyan"
        AppTheme.MONOCHROME -> "Monochrome"
        AppTheme.GRAPHITE -> "Graphite"
    }
}
