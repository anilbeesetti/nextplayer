package dev.anilbeesetti.nextplayer.core.media.sync

import android.net.Uri

interface MediaInfoSynchronizer {

    fun syncMediaInfoForMediumUri(uri: Uri)
}
