package dev.anilbeesetti.nextplayer.feature.player.model

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import dev.anilbeesetti.nextplayer.feature.player.extensions.getSubtitleMime

data class Subtitle(
    val name: String?,
    val uri: Uri,
    val isSelected: Boolean
)
