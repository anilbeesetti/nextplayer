package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@UnstableApi
fun TrackGroup.getName(trackType: @C.TrackType Int, index: Int): String {
    val format = this.getFormat(0)
    val language = format.language
    val label = format.label
    return buildString {
        if (label != null) {
            append(label)
        }
        if (isEmpty()) {
            append(
                when (trackType) {
                    C.TRACK_TYPE_TEXT -> "Subtitle Track #${index + 1}"
                    C.TRACK_TYPE_VIDEO -> "Video Track #${index + 1}"
                    else -> "Audio Track #${index + 1}"
                },
            )
        }

        @Suppress("DEPRECATION")
        if (language != null && language != "und") {
            append(" - ")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                append(Locale.of(language).displayLanguage)
            } else {
                append(Locale(language))
            }
        }
    }
}
