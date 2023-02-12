package dev.anilbeesetti.nextplayer

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaManager @Inject constructor(
    @ApplicationContext val context: Context
) {

    fun getVideos(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()
        // Create a content resolver
        val contentResolver = context.contentResolver

        // Define the content URI for video files
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

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
        val cursor = contentResolver.query(uri, projection, null, null, null)

        // Iterate through the cursor to retrieve the video data
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val title =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE))
                val duration =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                val data =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                val width =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
                val height =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT))
                val displayName =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))

                mediaList.add(
                    MediaItem(
                        id = id,
                        title = title,
                        duration = duration,
                        data = data,
                        displayName = displayName,
                        width = width,
                        height = height
                    )
                )
            }

            cursor.close()
        }

        return mediaList
    }

}



data class MediaItem(
    val id: Long,
    val title: String,
    val duration: Int,
    val data: String,
    val displayName: String,
    val width: Int,
    val height: Int
)