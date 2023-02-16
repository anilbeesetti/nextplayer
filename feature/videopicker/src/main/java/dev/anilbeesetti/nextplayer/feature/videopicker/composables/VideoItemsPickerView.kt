package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.data.util.VideoItem


@Composable
fun VideoItemsPickerView(
    videoItems: List<VideoItem>,
    onVideoItemClick: (uri: Uri) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        items(videoItems) { videoItem ->
            VideoItemView(
                videoItem = videoItem,
                onClick = { onVideoItemClick(videoItem.contentUri) }
            )
        }
    }
}
