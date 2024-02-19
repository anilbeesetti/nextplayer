package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import androidx.activity.ComponentActivity

interface MediaService {
    fun initialize(activity: ComponentActivity)
    suspend fun deleteMedia(uris: List<Uri>): Boolean
    suspend fun renameMedia(uri: Uri, to: String): Boolean
}
