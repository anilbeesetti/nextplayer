package dev.anilbeesetti.nextplayer.core.media.sync

import android.net.Uri

interface MediaInfoRetriever {

    fun sync(uri: Uri)

    suspend fun clearThumbnailsCache()
}
