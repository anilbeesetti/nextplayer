package dev.anilbeesetti.nextplayer.feature.player.model

import android.net.Uri
import androidx.compose.runtime.Stable

/** A subtitle file shown at the top of the screen for the media item with id [mediaId]. */
@Stable
data class SecondarySubtitle(
    val uri: Uri,
    val mediaId: String,
    val name: String,
)
