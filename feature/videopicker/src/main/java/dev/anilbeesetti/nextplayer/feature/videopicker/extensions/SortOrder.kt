package dev.anilbeesetti.nextplayer.feature.videopicker.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun SortOrder.name(sortBy: SortBy): String {
    val stringRes = when (sortBy) {
        SortBy.TITLE,
        SortBy.PATH -> when (this) {
            SortOrder.ASCENDING -> R.string.a_z
            SortOrder.DESCENDING -> R.string.z_a
        }
        SortBy.LENGTH -> when (this) {
            SortOrder.ASCENDING -> R.string.shortest
            SortOrder.DESCENDING -> R.string.longest
        }
        SortBy.SIZE -> when (this) {
            SortOrder.ASCENDING -> R.string.smallest
            SortOrder.DESCENDING -> R.string.largest
        }

        SortBy.DATE -> when (this) {
            SortOrder.ASCENDING -> R.string.oldest
            SortOrder.DESCENDING -> R.string.newest
        }
    }

    return stringResource(stringRes)
}
