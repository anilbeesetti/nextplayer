package dev.anilbeesetti.nextplayer.core.data.mediasource

import android.provider.MediaStore
import dev.anilbeesetti.nextplayer.core.data.models.Video
import kotlinx.coroutines.flow.Flow

interface MediaSource {

    /**
     * Get list of [Video]s as flow
     * @param selection selection of the query
     * @param selectionArgs selection arguments of the query
     * @param sortOrder sort order of the query
     * @return flow of list of [Video]
     * @see [android.content.ContentResolver.query]
     */
    fun getVideoItemsFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
    ): Flow<List<Video>>

    /**
     * Get list of [Video]s
     * @return list of video items
     */
    fun getVideoItems(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<Video>
}
