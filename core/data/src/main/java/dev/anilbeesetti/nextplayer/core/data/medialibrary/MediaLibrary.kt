package dev.anilbeesetti.nextplayer.core.data.medialibrary

import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import kotlinx.coroutines.flow.Flow

interface MediaLibrary {

    /**
     * Get list of [VideoItem]s as flow
     * @param selection selection of the query
     * @param selectionArgs selection arguments of the query
     * @param sortOrder sort order of the query
     * @return flow of list of [VideoItem]
     * @see [android.content.ContentResolver.query]
     */
    fun getVideoItemsFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): Flow<List<VideoItem>>

    /**
     * Get list of [VideoItem]s
     * @return list of video items
     */
    fun getVideoItems(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<VideoItem>
}
