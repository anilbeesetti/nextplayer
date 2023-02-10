package dev.anilbeesetti.nextplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import dev.anilbeesetti.nextplayer.ui.theme.NextPlayerTheme


private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NextPlayerTheme {

                val context = LocalContext.current
                val mediaList = remember { mutableStateListOf<MediaItem>() }

                LaunchedEffect(key1 = Unit) {
                    mediaList.addAll(scanMedia(context))
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Next Player",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                        LazyColumn() {
                            items(mediaList) { mediaItem ->
                                MediaItem(
                                    media = mediaItem,
                                    onClick = {
                                        val intent =
                                            Intent(context, PlayerActivity::class.java).also {
                                                it.putExtra("data", mediaItem.data)
                                            }
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


fun scanMedia(context: Context): List<MediaItem> {

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

data class MediaItem(
    val id: Long,
    val title: String,
    val duration: Int,
    val data: String,
    val displayName: String,
    val width: Int,
    val height: Int
)