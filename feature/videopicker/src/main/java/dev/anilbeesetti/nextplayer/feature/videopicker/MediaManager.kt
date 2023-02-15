package dev.anilbeesetti.nextplayer.feature.videopicker

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaManager @Inject constructor(
    @ApplicationContext val context: Context
) {

    suspend fun getVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val videoItems = mutableListOf<VideoItem>()
        // Create a content resolver
        val contentResolver = context.contentResolver

        // Define the content URI for video files
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // Define the columns to retrieve
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.DISPLAY_NAME
        )

        // Perform the query
        val cursor = contentResolver.query(collectionUri, projection, null, null, null)

        // Iterate through the cursor to retrieve the video data
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val title =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE))
                val duration =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                val width =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
                val height =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT))
                val displayName =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))

                videoItems.add(
                    VideoItem(
                        id = id,
                        nameWithExtension = title,
                        duration = duration,
                        displayName = displayName,
                        width = width,
                        height = height,
                        contentUri = ContentUris.withAppendedId(
                            collectionUri,
                            id
                        )
                    )
                )
            }

            cursor.close()
        }

        return@withContext videoItems
    }
}

data class VideoItem(
    val id: Long,
    val duration: Int,
    val contentUri: Uri,
    val displayName: String,
    val nameWithExtension: String,
    val width: Int,
    val height: Int
)
