package dev.anilbeesetti.nextplayer.core.media.mediasource

import android.provider.MediaStore
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import kotlinx.coroutines.flow.Flow

interface MediaSource {

    /**
     * Get list of [MediaVideo]s as flow
     * @param selection selection of the query
     * @param selectionArgs selection arguments of the query
     * @param sortOrder sort order of the query
     * @return flow of list of [MediaVideo]
     * @see [android.content.ContentResolver.query]
     */
    fun getMediaVideosFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
    ): Flow<List<MediaVideo>>

    /**
     * Get list of [MediaVideo]s
     * @return list of video items
     */
    fun getMediaVideo(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<MediaVideo>
}
