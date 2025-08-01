package com.nextplayer

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailUtils {
    private val cache = LruCache<String, Bitmap>(50)

    suspend fun getVideoThumbnailAsync(path: String): Bitmap? {
        cache.get(path)?.let { return it }
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val bmp = retriever.frameAtTime
                if (bmp != null) cache.put(path, bmp)
                bmp
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }
}
