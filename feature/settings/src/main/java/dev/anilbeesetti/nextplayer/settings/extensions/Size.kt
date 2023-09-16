package dev.anilbeesetti.nextplayer.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.model.Size
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun Size.name(): String {
    val stringRes = when (this) {
        Size.COMPACT -> R.string.compact
        Size.MEDIUM -> R.string.medium
        Size.LARGE -> R.string.large
    }

    return stringResource(id = stringRes)
}
