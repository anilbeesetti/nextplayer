package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.data.models.Video

@Composable
fun VideoItemsPickerView(
    videos: List<Video>,
    onVideoItemClick: (uri: Uri) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = Modifier.navigationBarsPadding()
    ) {
        items(videos) { video ->
            VideoItemView(
                video = video,
                onClick = { onVideoItemClick(video.uri) }
            )
        }
    }
}
