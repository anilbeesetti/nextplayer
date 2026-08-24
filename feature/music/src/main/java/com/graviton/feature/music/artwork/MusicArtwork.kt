package com.graviton.feature.music.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.graviton.core.ui.designsystem.NextIcons

/**
 * Ordered Coil models for a playing item: embedded/downloaded art first, then the media URI
 * so VideoThumbnailDecoder / AudioArtworkDecoder can extract a real picture.
 */
fun artworkModels(
    artworkUri: String?,
    mediaUri: String?,
    artworkData: ByteArray? = null,
): List<Any> = buildList {
    artworkData?.takeIf { it.isNotEmpty() }?.let(::add)
    artworkUri?.takeIf { it.isNotBlank() }?.let(::add)
    mediaUri?.takeIf { it.isNotBlank() && it != artworkUri }?.let(::add)
}

@Composable
fun MediaArtwork(
    artworkUri: String?,
    mediaUri: String?,
    modifier: Modifier = Modifier,
    artworkData: ByteArray? = null,
    fallback: ImageVector = NextIcons.Audio,
    corner: Dp = 14.dp,
) {
    val context = LocalContext.current
    val models = remember(artworkUri, mediaUri, artworkData) {
        artworkModels(artworkUri, mediaUri, artworkData)
    }
    var modelIndex by remember(models) { mutableStateOf(0) }
    val current = models.getOrNull(modelIndex)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (current == null) {
            Icon(
                imageVector = fallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(current)
                    .crossfade(true)
                    .listener(
                        onError = { _, _ ->
                            if (modelIndex < models.lastIndex) {
                                modelIndex += 1
                            }
                        },
                    )
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
