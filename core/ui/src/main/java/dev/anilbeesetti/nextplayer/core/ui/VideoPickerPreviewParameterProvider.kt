package dev.anilbeesetti.nextplayer.core.ui

import android.net.Uri
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem

class VideoPickerPreviewParameterProvider : PreviewParameterProvider<List<VideoItem>> {
    override val values: Sequence<List<VideoItem>>
        get() = sequenceOf(
            listOf(
                VideoItem(
                    id = 1,
                    path = "/storage/emulated/0/Download/The Shawshank Redemption (1994) 720p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "The Shawshank Redemption (1994) 720p BluRay x264.mp4",
                    duration = 1200,
                    displayName = "The Shawshank Redemption (1994) 720p BluRay x264",
                    width = 1280,
                    height = 720
                ),

                VideoItem(
                    id = 2,
                    path = "/storage/emulated/0/Download/The Godfather (1972) 1080p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "The Godfather (1972) 1080p BluRay x264.mp4",
                    duration = 1400,
                    displayName = "The Godfather (1972) 1080p BluRay x264",
                    width = 1920,
                    height = 1080
                ),

                VideoItem(
                    id = 3,
                    path = "/storage/emulated/0/Download/The Dark Knight (2008) 2160p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "The Dark Knight (2008) 2160p BluRay x264.mp4",
                    duration = 1500,
                    displayName = "The Dark Knight (2008) 2160p BluRay x264",
                    width = 3840,
                    height = 2160
                ),

                VideoItem(
                    id = 4,
                    path = "/storage/emulated/0/Download/The Godfather: Part II (1974) 720p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "The Godfather: Part II (1974) 720p BluRay x264.mp4",
                    duration = 1350,
                    displayName = "The Godfather: Part II (1974) 720p BluRay x264",
                    width = 1280,
                    height = 720
                ),

                VideoItem(
                    id = 5,
                    path = "/storage/emulated/0/Download/The Lord of the Rings: The Fellowship of the Ring (2001) 1080p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "The Lord of the Rings: The Fellowship of the Ring (2001) 1080p BluRay x264.mp4",
                    duration = 1800,
                    displayName = "The Lord of the Rings: The Fellowship of the Ring (2001) 1080p BluRay x264",
                    width = 1920,
                    height = 1080
                ),

                VideoItem(
                    id = 6,
                    path = "/storage/emulated/0/Download/The Lord of the Rings: The Two Towers (2002) 1080p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "The Lord of the Rings: The Two Towers (2002) 1080p BluRay x264.mp4",
                    duration = 2000,
                    displayName = "The Lord of the Rings: The Two Towers (2002) 1080p BluRay x264",
                    width = 1920,
                    height = 1080
                ),

                VideoItem(
                    id = 7,
                    path = "/storage/emulated/0/Download/The Lord of the Rings: The Return of the King (2003) 1080p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "The Lord of the Rings: The Return of the King (2003) 1080p BluRay x264.mp4",
                    duration = 2100,
                    displayName = "The Lord of the Rings: The Return of the King (2003) 1080p BluRay x264",
                    width = 1920,
                    height = 1080
                ),

                VideoItem(
                    id = 8,
                    path = "/storage/emulated/0/Download/Pulp Fiction (1994) 720p BluRay x264.mp4",
                    contentUri = Uri.EMPTY,
                    nameWithExtension = "Star Wars: Episode IV - A New Hope (1977) 2160p BluRay x264.mp4",
                    duration = 1500,
                    displayName = "Star Wars: Episode IV - A New Hope (1977) 2160p BluRay x264",
                    width = 3840,
                    height = 2160
                )
            )
        )
}
